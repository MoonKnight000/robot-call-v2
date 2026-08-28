package uz.murodjon.uysotvoice.agent.summary;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.agent.dialog.CallSummary;
import uz.murodjon.uysotvoice.scenario.dto.OutcomeField;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioDefinition;
import uz.murodjon.uysotvoice.shared.dialog.Sentiment;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Produces the structured {@link CallSummary} from a finished call's transcript
 * (PROJECT.md §4.3, ROADMAP A.3). A single LLM call with a stronger model.
 * The fields every scenario gets ({@code summary}/{@code sentiment}/{@code needsFollowUp}/
 * {@code followUpNote}/{@code callbackAt}) are fixed; everything else asked for comes from
 * the call's {@code ScenarioDefinition.outcomeSchema()}.
 */
@Service
public class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

    private static final Set<String> FIXED_FIELDS = Set.of(
            "summary", "sentiment", "needsFollowUp", "followUpNote", "callbackAt");

    private static final String SYSTEM_PROMPT_HEADER = """
            Siz qo'ng'iroq transkriptini tahlil qiluvchi yordamchisiz.
            Sizga AGENT va CLIENT gaplaridan iborat transkript beriladi.
            Undan quyidagi maydonlarni ajratib, faqat so'ralgan struktura bo'yicha JSON qaytaring:
            - summary: 2-3 jumlada CRM uchun qisqacha xulosa (o'zbekcha).
            - sentiment: mijoz kayfiyati (POSITIVE, NEUTRAL, NEGATIVE, HOSTILE).
            - needsFollowUp: qayta qo'ng'iroq kerakmi (true/false).
            - followUpNote: qayta qo'ng'iroq uchun izoh (bo'lmasa null).
            - callbackAt: mijoz qayta qo'ng'iroq qilishni so'ragan aniq sana va vaqt (masalan: "kechki 5 da", "ertaga soat 14:00 da" -> ISO-8601 formatida YYYY-MM-DDTHH:mm:ss, bo'lmasa null).
            """;

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final boolean enabled;
    private final String model;
    private final String reasoningEffort;
    private final int maxTokens;

    private volatile ChatClient chatClient;

    public SummaryService(ObjectProvider<ChatModel> chatModelProvider,
                          @Value("${voice-agent.summary.enabled:true}") boolean enabled,
                          @Value("${voice-agent.summary.model:gemini-3.6-flash}") String model,
                          @Value("${voice-agent.summary.reasoning-effort:low}") String reasoningEffort,
                          @Value("${voice-agent.summary.max-tokens:2048}") int maxTokens) {
        this.chatModelProvider = chatModelProvider;
        this.enabled = enabled;
        this.model = model;
        this.reasoningEffort = reasoningEffort;
        this.maxTokens = maxTokens;
    }

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("Call summary disabled (voice-agent.summary.enabled=false)");
            return;
        }
        ChatModel cm = chatModelProvider.getIfAvailable();
        if (cm != null) {
            chatClient = ChatClient.create(cm);
            log.info("Summary service ready (model={})", model);
        } else {
            log.warn("Summary service has no LLM ChatModel (set GEMINI_API_KEY); summaries disabled");
        }
    }

    /** Summarize {@code transcript} against {@code scenario}'s outcomeSchema; {@code null} on empty input or failure. */
    public CallSummary summarize(String transcript, ScenarioDefinition scenario) {
        if (chatClient == null || transcript == null || transcript.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> raw = chatClient.prompt()
                    .options(GoogleGenAiChatOptions.builder()
                            .model(model)
                            .maxOutputTokens(maxTokens)
                            .thinkingLevel(thinkingLevel())
                            .build())
                    .system(systemPrompt(scenario) + "\nBUGUNGI SANA VA VAQT: " + java.time.LocalDateTime.now() + ".")
                    .user(transcript)
                    .call()
                    .entity(new MapOutputConverter());
            return toCallSummary(raw, scenario);
        } catch (Exception e) {
            log.warn("Summary generation failed: {}", e.getMessage());
            return null;
        }
    }

    /** The fixed fields every scenario gets, plus one instruction line per {@code outcomeSchema} field. */
    private static String systemPrompt(ScenarioDefinition scenario) {
        StringBuilder sb = new StringBuilder(SYSTEM_PROMPT_HEADER);
        List<OutcomeField> outcomeSchema = scenario.outcomeSchema();
        if (outcomeSchema != null) {
            for (OutcomeField f : outcomeSchema) {
                sb.append("- ").append(f.name()).append(" (").append(f.type()).append("): ")
                        .append(f.description() != null ? f.description() : "").append('\n');
            }
        }
        sb.append("Ma'lum bo'lmagan maydonni null qoldiring. Transkriptda nisbiy sana bo'lsa "
                + "(\"ertaga\", \"kelasi oyning 5-sanasi\"), uni BUGUNGI SANA VA VAQTdan hisoblang — yilni "
                + "o'zingizdan to'qimang. Faktlarni o'ylab topmang — faqat transkriptdagi ma'lumotga tayaning.");
        return sb.toString();
    }

    /** Splits the model's raw JSON map into the 5 fixed fields plus a scenario-shaped outcome map. */
    private static CallSummary toCallSummary(Map<String, Object> raw, ScenarioDefinition scenario) {
        if (raw == null) {
            return null;
        }
        String summary = str(raw.get("summary"));
        Sentiment sentiment = sentiment(raw.get("sentiment"));
        boolean needsFollowUp = Boolean.TRUE.equals(raw.get("needsFollowUp"));
        String followUpNote = str(raw.get("followUpNote"));
        String callbackAt = str(raw.get("callbackAt"));

        Map<String, Object> outcome = new HashMap<>();
        List<OutcomeField> outcomeSchema = scenario.outcomeSchema();
        if (outcomeSchema != null) {
            for (OutcomeField f : outcomeSchema) {
                Object value = raw.get(f.name());
                if (value != null && !FIXED_FIELDS.contains(f.name())) {
                    outcome.put(f.name(), value);
                }
            }
        }
        return new CallSummary(summary, outcome, sentiment, needsFollowUp, followUpNote, callbackAt);
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static Sentiment sentiment(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Sentiment.valueOf(value.toString().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown sentiment '{}' from summary model", value);
            return null;
        }
    }

    private GoogleGenAiThinkingLevel thinkingLevel() {
        if (reasoningEffort == null) {
            return GoogleGenAiThinkingLevel.LOW;
        }
        return switch (reasoningEffort.trim().toUpperCase()) {
            case "MINIMAL", "OFF" -> GoogleGenAiThinkingLevel.MINIMAL;
            case "MEDIUM" -> GoogleGenAiThinkingLevel.MEDIUM;
            case "HIGH" -> GoogleGenAiThinkingLevel.HIGH;
            default -> GoogleGenAiThinkingLevel.LOW;
        };
    }
}
