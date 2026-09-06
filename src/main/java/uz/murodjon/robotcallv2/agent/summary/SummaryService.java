package uz.murodjon.robotcallv2.agent.summary;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.dialog.CallSummary;
import uz.murodjon.robotcallv2.scenario.domain.entity.OutcomeField;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.Sentiment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Produces the structured {@link CallSummary} from a finished call's transcript
 * (PROJECT.md §4.3, MASTER_ROADMAP.md §10). A single LLM call with a stronger model.
 * The fields every scenario gets ({@code summary}/{@code sentiment}/{@code needsFollowUp}/
 * {@code followUpNote}/{@code callbackAt}/{@code qaScore}/{@code commitmentScore}) are fixed;
 * everything else asked for comes from the call's {@code ScenarioDefinition.outcomeSchema()}.
 */
@Service
public class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

    private static final Set<String> FIXED_FIELDS = Set.of(
            "summary", "sentiment", "needsFollowUp", "followUpNote", "callbackAt", "qaScore", "commitmentScore");

    private static final String SYSTEM_PROMPT_HEADER = """
            Siz qo'ng'iroq transkriptini chuqur tahlil qiluvchi va baholovchi QA ekspertisiz.
            Sizga AGENT va CLIENT gaplaridan iborat transkript beriladi.
            Undan quyidagi maydonlarni ajratib, faqat so'ralgan struktura bo'yicha JSON qaytaring:
            - summary: 2-3 jumlada CRM uchun qisqacha xulosa (o'zbekcha).
            - sentiment: mijoz kayfiyati (POSITIVE, NEUTRAL, NEGATIVE, HOSTILE).
            - needsFollowUp: qayta qo'ng'iroq kerakmi (true/false).
            - followUpNote: qayta qo'ng'iroq uchun izoh (bo'lmasa null).
            - callbackAt: mijoz qayta qo'ng'iroq qilishni so'ragan sana-vaqt (ISO-8601 YYYY-MM-DDTHH:mm:ss, bo'lmasa null).
            - qaScore: suhbat sifati va botning stsenariyga muvofiqligi (0 dan 100 gacha butun son).
            - commitmentScore: mijozning to'lashga yoki kelishuvga rozilik darajasi (0 dan 100 gacha butun son).
            """;

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final boolean enabled;
    private final String model;
    private final String reasoningEffort;
    private final int maxTokens;

    private volatile ChatClient chatClient;

    public SummaryService(ObjectProvider<ChatModel> chatModelProvider, SummaryProperties summaryProperties) {
        this.chatModelProvider = chatModelProvider;
        this.enabled = summaryProperties.enabled();
        this.model = summaryProperties.model();
        this.reasoningEffort = summaryProperties.reasoningEffort();
        this.maxTokens = summaryProperties.maxTokens();
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
            log.warn("Summary service has no LLM ChatModel (set GEMINI_API_KEY or GROQ_API_KEY); summaries disabled");
        }
    }

    /** Summarize {@code transcript} against {@code scenario}'s outcomeSchema; {@code null} on empty input or failure. */
    public CallSummary summarize(String transcript, ScenarioDefinition scenario) {
        if (chatClient == null || transcript == null || transcript.isBlank()) {
            return null;
        }
        try {
            ChatModel cm = chatModelProvider.getIfAvailable();
            boolean isOpenAi = (cm instanceof OpenAiChatModel)
                    || (model != null && (model.startsWith("llama") || model.startsWith("mixtral")
                    || model.startsWith("gpt-") || model.startsWith("qwen")));
            ChatOptions options;
            if (isOpenAi) {
                options = OpenAiChatOptions.builder()
                        .model(model)
                        .maxTokens(maxTokens)
                        .build();
            } else {
                options = GoogleGenAiChatOptions.builder()
                        .model(model)
                        .maxOutputTokens(maxTokens)
                        .thinkingLevel(thinkingLevel())
                        .build();
            }
            Map<String, Object> raw = chatClient.prompt()
                    .options(options)
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
        sb.append("Ma'lum bo'lmagan maydonni null qoldiring. Transkriptda nisbiy sana bo'lsa ")
                .append("(\"ertaga\", \"kelasi oyning 5-sanasi\"), uni BUGUNGI SANA VA VAQTdan hisoblang — yilni ")
                .append("o'zingizdan to'qimang. Faktlarni o'ylab topmang — faqat transkriptdagi ma'lumotga tayaning.");
        return sb.toString();
    }

    /** Splits the model's raw JSON map into the fixed fields plus a scenario-shaped outcome map. */
    private static CallSummary toCallSummary(Map<String, Object> raw, ScenarioDefinition scenario) {
        if (raw == null) {
            return null;
        }
        String summary = str(raw.get("summary"));
        Sentiment sentiment = sentiment(raw.get("sentiment"));
        boolean needsFollowUp = Boolean.TRUE.equals(raw.get("needsFollowUp"));
        String followUpNote = str(raw.get("followUpNote"));
        String callbackAt = str(raw.get("callbackAt"));
        Integer qaScore = integer(raw.get("qaScore"), 100);
        Integer commitmentScore = integer(raw.get("commitmentScore"), 50);

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
        return new CallSummary(summary, outcome, sentiment, needsFollowUp, followUpNote, callbackAt, qaScore, commitmentScore);
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static Integer integer(Object value, Integer fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            if (value instanceof Number n) {
                return n.intValue();
            }
            return Integer.parseInt(value.toString().trim());
        } catch (Exception e) {
            return fallback;
        }
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
            case "HIGH" -> GoogleGenAiThinkingLevel.HIGH;
            case "LOW" -> GoogleGenAiThinkingLevel.LOW;
            default -> GoogleGenAiThinkingLevel.THINKING_LEVEL_UNSPECIFIED;
        };
    }
}
