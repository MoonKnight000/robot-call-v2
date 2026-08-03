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
 * (PROJECT.md §4.3, ROADMAP A.3). A single LLM call with a stronger model (Sonnet —
 * quality matters, latency does not). The 4 fields every scenario always gets
 * ({@code summary}/{@code sentiment}/{@code needsFollowUp}/{@code followUpNote}) are
 * fixed; everything else asked for comes from the call's {@code ScenarioDefinition
 * .outcomeSchema()}, so a {@code survey} call gets asked for {@code answers}/{@code
 * score} instead of a debt-collection call's {@code reasonCode}/{@code promisedDate}.
 * Non-fatal: returns {@code null} if the LLM is unavailable or parsing fails.
 */
@Service
public class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

    private static final Set<String> FIXED_FIELDS = Set.of("summary", "sentiment", "needsFollowUp", "followUpNote");

    private static final String SYSTEM_PROMPT_HEADER = """
            Siz qo'ng'iroq transkriptini tahlil qiluvchi yordamchisiz.
            Sizga AGENT va CLIENT gaplaridan iborat transkript beriladi.
            Undan quyidagi maydonlarni ajratib, faqat so'ralgan struktura bo'yicha JSON qaytaring:
            - summary: 2-3 jumlada CRM uchun qisqacha xulosa (o'zbekcha).
            - sentiment: mijoz kayfiyati (POSITIVE, NEUTRAL, NEGATIVE, HOSTILE).
            - needsFollowUp: qayta qo'ng'iroq kerakmi (true/false).
            - followUpNote: qayta qo'ng'iroq uchun izoh (bo'lmasa null).
            """;

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final boolean enabled;
    private final String model;
    private final String reasoningEffort;
    private final int maxTokens;

    private volatile ChatClient chatClient;

    public SummaryService(ObjectProvider<ChatModel> chatModelProvider,
                          @Value("${voice-agent.summary.enabled:true}") boolean enabled,
                          @Value("${voice-agent.summary.model:gemini-2.5-flash}") String model,
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
            // Every field left unset here falls back to spring.ai.google.genai.chat.options
            // (Spring AI merges runtime over defaults), and those defaults are tuned for
            // the live phone turn: 512 tokens, thinking MINIMAL. A structured summary wants
            // the opposite — room for the JSON and some reasoning — so state both.
            Map<String, Object> raw = chatClient.prompt()
                    .options(GoogleGenAiChatOptions.builder()
                            .model(model)
                            .maxOutputTokens(maxTokens)
                            .thinkingLevel(thinkingLevel())
                            .build())
                    // The transcript carries no year, so the date has to come from us.
                    .system(systemPrompt(scenario) + "\nBUGUNGI SANA: " + LocalDate.now() + ".")
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
                + "(\"ertaga\", \"kelasi oyning 5-sanasi\"), uni BUGUNGI SANAdan hisoblang — yilni "
                + "o'zingizdan to'qimang. Faktlarni o'ylab topmang — faqat transkriptdagi ma'lumotga tayaning.");
        return sb.toString();
    }

    /** Splits the model's raw JSON map into the 4 fixed fields plus a scenario-shaped outcome map. */
    private static CallSummary toCallSummary(Map<String, Object> raw, ScenarioDefinition scenario) {
        if (raw == null) {
            return null;
        }
        String summary = str(raw.get("summary"));
        Sentiment sentiment = sentiment(raw.get("sentiment"));
        boolean needsFollowUp = Boolean.TRUE.equals(raw.get("needsFollowUp"));
        String followUpNote = str(raw.get("followUpNote"));

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
        return new CallSummary(summary, outcome, sentiment, needsFollowUp, followUpNote);
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

    /**
     * {@code voice-agent.summary.reasoning-effort} as a Gemini thinking level. The
     * property keeps its name (and {@code SUMMARY_REASONING_EFFORT} its meaning) across
     * the move off the OpenAI-compatible endpoint, where the same idea was spelled
     * "reasoning effort" — the values MINIMAL/LOW/MEDIUM/HIGH line up. An unknown value
     * falls back to LOW rather than failing the summary: this runs after the call is
     * over, and losing the CRM note over a typo in an env var is the worse outcome.
     */
    private GoogleGenAiThinkingLevel thinkingLevel() {
        try {
            return GoogleGenAiThinkingLevel.valueOf(reasoningEffort.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("Unknown summary reasoning-effort '{}', using LOW", reasoningEffort);
            return GoogleGenAiThinkingLevel.LOW;
        }
    }
}
