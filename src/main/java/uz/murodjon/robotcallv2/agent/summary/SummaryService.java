package uz.murodjon.robotcallv2.agent.summary;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.dialog.CallSummary;
import uz.murodjon.robotcallv2.agent.dialog.PiiRedactor;
import uz.murodjon.robotcallv2.agent.dialog.TokenUsage;
import uz.murodjon.robotcallv2.agent.provider.OwnLlmChatModels;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.DataEvaluationCriterion;
import uz.murodjon.robotcallv2.aiagent.domain.entity.DataExtractionField;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelConfigUseCase;
import uz.murodjon.robotcallv2.aimodel.domain.entity.OwnLlmEndpoint;
import uz.murodjon.robotcallv2.scenario.domain.entity.OutcomeField;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.Sentiment;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;
import uz.murodjon.robotcallv2.shared.util.PromptText;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * Produces the structured {@link CallSummary} from a finished call's transcript
 * (PROJECT.md §4.3, MASTER_ROADMAP.md §10). A single LLM call with a stronger model.
 * The fields every scenario gets ({@code summary}/{@code sentiment}/{@code needsFollowUp}/
 * {@code followUpNote}/{@code callbackAt}/{@code qaScore}/{@code commitmentScore}) are fixed;
 * everything else asked for comes from the call's {@code ScenarioDefinition.outcomeSchema()}
 * and the agent's {@code dataNeeded} / {@code dataEvaluation} analysis configuration.
 *
 * <p>The agent's analysis is structured (NEXT-PLAN A.5): each {@code dataNeeded} value is
 * read in its declared type and format — a value that does not parse earns the model one more try,
 * then its key lands in {@code extractionErrors} — and each {@code dataEvaluation}
 * criterion comes back as {@code {verdict: PASS|FAIL|N/A, reason}} under
 * {@code outcome.evaluation}.
 */
@Service
public class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

    private static final Set<String> FIXED_FIELDS = Set.of(
            "summary", "sentiment", "needsFollowUp", "followUpNote", "callbackAt", "qaScore", "commitmentScore", "topics");

    /** Where the per-criterion verdicts live, in the model's answer and in {@code outcome}. */
    private static final String EVALUATION_KEY = "evaluation";
    /** The {@code outcome} key listing {@code dataNeeded} values that never came back in their type. */
    private static final String EXTRACTION_ERRORS_KEY = "extractionErrors";
    /** Where what the call was about lives, in the model's answer and in {@code outcome} — counted by the call-topics report. */
    private static final String TOPICS_KEY = "topics";
    private static final int MAX_TOPICS = 3;
    private static final int MAX_TOPIC_CHARS = 60;
    private static final Pattern EMAIL_ADDRESS = Pattern.compile(
            "^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
                    + "@(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}$");

    private static final String SYSTEM_PROMPT_HEADER = PromptText.text("call-summary.txt");

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final boolean enabled;
    private final String model;
    private final String reasoningEffort;
    private final int maxTokens;
    private final long timeoutMs;
    private final AiModelConfigUseCase aiModelConfigUseCase;
    private final OwnLlmChatModels ownLlmChatModels;

    /** Runs the summary requests, so {@link #ask} can stop waiting on one. */
    private static final ExecutorService SUMMARY_CALLS = Executors.newVirtualThreadPerTaskExecutor();

    private volatile ChatClient chatClient;

    public SummaryService(ObjectProvider<ChatModel> chatModelProvider, SummaryProperties summaryProperties,
                          AiModelConfigUseCase aiModelConfigUseCase, OwnLlmChatModels ownLlmChatModels) {
        this.chatModelProvider = chatModelProvider;
        this.aiModelConfigUseCase = aiModelConfigUseCase;
        this.ownLlmChatModels = ownLlmChatModels;
        this.enabled = summaryProperties.enabled();
        this.model = summaryProperties.model();
        this.reasoningEffort = summaryProperties.reasoningEffort();
        this.maxTokens = summaryProperties.maxTokens();
        this.timeoutMs = summaryProperties.timeoutMs();
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
            log.warn("Summary service has no LLM ChatModel (set GEMINI_API_KEY or OPENAI_API_KEY); summaries disabled");
        }
    }

    /**
     * One more question about a finished call, on the summary's model and under its rules: a
     * company on its own LLM is asked there, never on the platform's model.
     *
     * @return the model's JSON answer, or null when summaries are off, the transcript is empty,
     *         no model is available or the request failed
     */
    public Map<String, Object> askAboutCall(long companyId, String system, String transcript) {
        if (!enabled || transcript == null || transcript.isBlank()) {
            return null;
        }
        try {
            OwnLlmEndpoint ownLlm = aiModelConfigUseCase.findEffectiveByCompanyId(companyId).ownLlm();
            ChatClient client = ownLlm != null
                    ? ChatClient.create(ownLlmChatModels.createChatModel(companyId, ownLlm))
                    : chatClient;
            if (client == null) {
                return null;
            }
            ChatOptions options = ownLlm != null
                    ? OpenAiChatOptions.builder().model(ownLlm.model()).maxTokens(maxTokens).build()
                    : options();
            return ask(client, options, system, transcript, new TokenUsage());
        } catch (Exception e) {
            log.warn("Question about a call failed (company {}): {}", companyId, e.getMessage());
            return null;
        }
    }

    /** Whether summaries can be made at all: enabled, with a chat model to make them. */
    public boolean isAvailable() {
        return chatClient != null;
    }

    /** Summarize {@code transcript} against {@code scenario}'s outcomeSchema; {@code null} on empty input or failure. */
    public SummarizedCall summarize(long companyId, String transcript, ScenarioDefinition scenario) {
        return summarize(companyId, transcript, scenario, null);
    }

    /**
     * Summarizes {@code transcript} against {@code scenario}'s outcomeSchema and agent's
     * {@code dataNeeded} & {@code dataEvaluation} analysis configuration.
     *
     * @return the summary with the tokens it took, which are charged to the call; {@code null}
     *         on empty input or failure
     */
    public SummarizedCall summarize(long companyId, String transcript, ScenarioDefinition scenario, AiAgent agent) {
        return summarize(companyId, transcript, scenario, agent, java.time.LocalDateTime.now());
    }

    /**
     * As {@link #summarize(long, String, ScenarioDefinition, AiAgent)}, with relative dates in the
     * transcript ("ertaga") read from {@code callTime} — when the call took place — rather
     * than from now: a reanalysis of last month's call must not move its dates to this month.
     */
    public SummarizedCall summarize(long companyId, String transcript, ScenarioDefinition scenario, AiAgent agent,
                                    java.time.LocalDateTime callTime) {
        if (!enabled || transcript == null || transcript.isBlank()) {
            return null;
        }
        TokenUsage tokens = new TokenUsage();
        try {
            // A company on its own LLM is summarized there too — never on the platform's model,
            // so a key that cannot be read leaves the summary for the outbox to retry.
            OwnLlmEndpoint ownLlm = aiModelConfigUseCase.findEffectiveByCompanyId(companyId).ownLlm();
            ChatClient client = ownLlm != null
                    ? ChatClient.create(ownLlmChatModels.createChatModel(companyId, ownLlm))
                    : chatClient;
            if (client == null) {
                return null;
            }
            ChatOptions options = ownLlm != null
                    ? OpenAiChatOptions.builder().model(ownLlm.model()).maxTokens(maxTokens).build()
                    : options();
            String system = systemPrompt(scenario, agent) + "\nTODAY'S DATE AND TIME: " + callTime + ".";
            Map<String, Object> raw = ask(client, options, system, transcript, tokens);
            List<String> invalid = invalidExtractions(raw, agent);
            if (!invalid.isEmpty()) {
                // One more try, told exactly which fields came back in the wrong shape. The
                // first answer stays if the retry fails: it is still a usable summary.
                log.info("Summary: {} extracted field(s) had an invalid type or format, asking once more: {}", invalid.size(), invalid);
                try {
                    raw = ask(client, options, system + "\nIN THE PREVIOUS ANSWER THESE FIELDS HAD AN INVALID TYPE OR FORMAT: "
                            + String.join(", ", invalid)
                            + ". Return them in the stated type or leave them null.", transcript, tokens);
                } catch (Exception e) {
                    log.warn("Summary retry for field types failed, keeping the first answer: {}", e.getMessage());
                }
            }
            CallSummary summary = toCallSummary(raw, scenario, agent);
            return summary == null ? null : new SummarizedCall(summary,
                    tokens.promptTokens(), tokens.completionTokens(), tokens.cachedTokens(), ownLlm != null);
        } catch (Exception e) {
            log.warn("Summary generation failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * One summary request, with {@code voice-agent.summary.timeout-ms} on it: the blocking
     * call has no deadline of its own, and the call's outcome waits on this one. What the
     * request cost is added to {@code tokens}.
     */
    private Map<String, Object> ask(ChatClient client, ChatOptions options, String system, String transcript,
                                    TokenUsage tokens)
            throws Exception {
        CompletableFuture<ResponseEntity<ChatResponse, Map<String, Object>>> pending =
                CompletableFuture.supplyAsync(() -> client.prompt()
                        .options(options.mutate())
                        .system(system)
                        .user(transcript)
                        .call()
                        .responseEntity(new MapOutputConverter()), SUMMARY_CALLS);
        try {
            ResponseEntity<ChatResponse, Map<String, Object>> answer = pending.get(timeoutMs, TimeUnit.MILLISECONDS);
            tokens.add(answer.response());
            return answer.entity();
        } catch (TimeoutException e) {
            pending.cancel(true);
            throw new TimeoutException("summary took longer than " + timeoutMs + " ms");
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
    }

    private ChatOptions options() {
        ChatModel cm = chatModelProvider.getIfAvailable();
        boolean isOpenAi = (cm instanceof OpenAiChatModel)
                || (model != null && (model.startsWith("llama") || model.startsWith("mixtral")
                || model.startsWith("gpt-") || model.startsWith("qwen")));
        if (isOpenAi) {
            return OpenAiChatOptions.builder()
                    .model(model)
                    .maxTokens(maxTokens)
                    .build();
        }
        return GoogleGenAiChatOptions.builder()
                .model(model)
                .maxOutputTokens(maxTokens)
                .thinkingLevel(thinkingLevel())
                .build();
    }

    /** The fixed fields every scenario gets, plus instructions for outcomeSchema, dataNeeded, and dataEvaluation. */
    private static String systemPrompt(ScenarioDefinition scenario, AiAgent agent) {
        StringBuilder sb = new StringBuilder(SYSTEM_PROMPT_HEADER);
        if (scenario != null) {
            List<OutcomeField> outcomeSchema = scenario.outcomeSchema();
            if (outcomeSchema != null) {
                for (OutcomeField f : outcomeSchema) {
                    sb.append("- ").append(f.name()).append(" (").append(f.type()).append("): ")
                            .append(f.description() != null ? f.description() : "").append('\n');
                }
            }
        }
        if (agent != null) {
            if (!agent.dataNeeded().isEmpty()) {
                sb.append("\nExtract the following fields from the transcript (data_needed). The JSON key is ")
                        .append("the name before the brackets; the value is in the stated type: string — text, number — ")
                        .append("digits only, boolean — true or false, date — YYYY-MM-DD, enum — one of the options ")
                        .append("in the description, phone — a valid UZ local or international number, email — a valid email address:\n");
                for (DataExtractionField f : agent.dataNeeded()) {
                    sb.append("- ").append(fieldKey(f)).append(" (").append(typeOf(f)).append("): ")
                            .append(f.name() != null ? f.name() : "").append(" — ")
                            .append(f.description() != null ? f.description() : "").append('\n');
                }
            }
            if (!agent.dataEvaluation().isEmpty()) {
                sb.append("\nScore each of the following criteria and write it into the \"").append(EVALUATION_KEY)
                        .append("\" object: key — the name before the colon, value — ")
                        .append("{\"verdict\": \"PASS\" | \"FAIL\" | \"N/A\", \"reason\": \"one-sentence reason\"}. ")
                        .append("N/A — the conversation gave no chance to check this criterion:\n");
                for (DataEvaluationCriterion c : agent.dataEvaluation()) {
                    sb.append("- ").append(criterionKey(c)).append(": ")
                            .append(c.name() != null ? c.name() : "").append(" — ")
                            .append(c.criteria() != null ? c.criteria() : "").append('\n');
                }
            }
        }
        sb.append("Leave a field you do not know null. If the transcript has a relative date ")
                .append("(\"ertaga\", \"kelasi oyning 5-sanasi\"), work it out from TODAY'S DATE AND TIME — never ")
                .append("invent the year. Do not make facts up — rely only on what is in the transcript.");
        return sb.toString();
    }

    /** Splits the model's raw JSON map into the fixed fields plus a scenario-shaped outcome map and agent analysis. */
    private static CallSummary toCallSummary(Map<String, Object> raw, ScenarioDefinition scenario, AiAgent agent) {
        if (raw == null) {
            return null;
        }
        String summary = PiiRedactor.redact(str(raw.get("summary")));
        Sentiment sentiment = sentiment(raw.get("sentiment"));
        boolean needsFollowUp = Boolean.TRUE.equals(raw.get("needsFollowUp"));
        String followUpNote = PiiRedactor.redact(str(raw.get("followUpNote")));
        String callbackAt = str(raw.get("callbackAt"));
        Integer qaScore = integer(raw.get("qaScore"), 100);
        Integer commitmentScore = integer(raw.get("commitmentScore"), 50);

        Map<String, Object> outcome = new HashMap<>();
        List<String> topics = topicsOf(raw.get(TOPICS_KEY));
        if (!topics.isEmpty()) {
            outcome.put(TOPICS_KEY, topics);
        }
        if (scenario != null) {
            List<OutcomeField> outcomeSchema = scenario.outcomeSchema();
            if (outcomeSchema != null) {
                for (OutcomeField f : outcomeSchema) {
                    Object value = raw.get(f.name());
                    if (value != null && !FIXED_FIELDS.contains(f.name())) {
                        outcome.put(f.name(), value);
                    }
                }
            }
        }
        if (agent != null) {
            for (DataExtractionField f : agent.dataNeeded()) {
                Object value = coerce(raw.get(fieldKey(f)), typeOf(f));
                if (value != null) {
                    outcome.put(fieldKey(f), value);
                }
            }
            Map<String, Object> evaluation = evaluation(raw.get(EVALUATION_KEY), agent.dataEvaluation());
            if (!evaluation.isEmpty()) {
                outcome.put(EVALUATION_KEY, evaluation);
            }
            List<String> invalid = invalidExtractions(raw, agent);
            if (!invalid.isEmpty()) {
                outcome.put(EXTRACTION_ERRORS_KEY, invalid);
            }
        }
        return new CallSummary(summary, outcome, sentiment, needsFollowUp, followUpNote, callbackAt, qaScore, commitmentScore);
    }

    /**
     * The {@code outcome} keys {@code agent}'s analysis writes: its {@code dataNeeded} keys,
     * {@code evaluation} and {@code extractionErrors}. What a reanalysis replaces.
     */
    public static List<String> findAnalysisKeys(AiAgent agent) {
        List<String> keys = new ArrayList<>();
        for (DataExtractionField f : agent.dataNeeded()) {
            keys.add(fieldKey(f));
        }
        keys.add(EVALUATION_KEY);
        keys.add(EXTRACTION_ERRORS_KEY);
        return keys;
    }

    /** {@code dataNeeded} keys whose value came back but cannot be read in the declared type. */
    private static List<String> invalidExtractions(Map<String, Object> raw, AiAgent agent) {
        List<String> invalid = new ArrayList<>();
        if (raw == null || agent == null) {
            return invalid;
        }
        for (DataExtractionField f : agent.dataNeeded()) {
            Object value = raw.get(fieldKey(f));
            if (isPresent(value) && coerce(value, typeOf(f)) == null) {
                invalid.add(fieldKey(f));
            }
        }
        return invalid;
    }

    /** Every criterion, answered or not: one the model skipped is {@code N/A}, not missing. */
    private static Map<String, Object> evaluation(Object rawEvaluation, List<DataEvaluationCriterion> criteria) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<?, ?> given = rawEvaluation instanceof Map<?, ?> map ? map : Map.of();
        for (DataEvaluationCriterion c : criteria) {
            Object entry = given.get(criterionKey(c));
            Map<String, Object> verdict = new LinkedHashMap<>();
            if (entry instanceof Map<?, ?> answer) {
                verdict.put("verdict", verdict(answer.get("verdict")));
                verdict.put("reason", str(answer.get("reason")));
            } else {
                verdict.put("verdict", verdict(entry));
                verdict.put("reason", null);
            }
            result.put(criterionKey(c), verdict);
        }
        return result;
    }

    private static String verdict(Object value) {
        String text = value == null ? "" : value.toString().trim().toUpperCase(Locale.ROOT);
        return switch (text) {
            case "PASS", "TRUE" -> "PASS";
            case "FAIL", "FALSE" -> "FAIL";
            default -> "N/A";
        };
    }

    /**
     * The value in its declared type, or null when it is missing or cannot be read as one.
     * Dates stay text ({@code YYYY-MM-DD}) so the outcome serializes without a date module.
     */
    static Object coerce(Object value, String type) {
        if (!isPresent(value)) {
            return null;
        }
        String text = value.toString().trim();
        try {
            return switch (type) {
                case "number" -> value instanceof Number ? value : new BigDecimal(text.replace(" ", ""));
                case "boolean" -> value instanceof Boolean ? value : booleanOf(text);
                case "date" -> LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text).toString();
                case "phone" -> validPhone(text) ? text : null;
                case "email" -> validEmail(text) ? text : null;
                default -> text;
            };
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean validPhone(String text) {
        String normalized = PhoneNumbers.normalize(text);
        return normalized != null && PhoneNumbers.E164_REGEX.matches(normalized);
    }

    private static boolean validEmail(String text) {
        int at = text.indexOf('@');
        return text.length() <= 254 && at > 0 && at <= 64 && EMAIL_ADDRESS.matcher(text).matches();
    }

    private static Boolean booleanOf(String text) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "true", "ha", "yes" -> Boolean.TRUE;
            case "false", "yo'q", "no" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static boolean isPresent(Object value) {
        return value != null && !value.toString().isBlank() && !"null".equalsIgnoreCase(value.toString().trim());
    }

    /** A field's key in the answer and in {@code outcome}: its stable id, its label when it has none. */
    private static String fieldKey(DataExtractionField f) {
        return f.id() != null && !f.id().isBlank() ? f.id().trim() : f.name();
    }

    private static String criterionKey(DataEvaluationCriterion c) {
        return c.id() != null && !c.id().isBlank() ? c.id().trim() : c.name();
    }

    private static String typeOf(DataExtractionField f) {
        return f.type() == null || f.type().isBlank() ? "string" : f.type().trim().toLowerCase(Locale.ROOT);
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * Lowercased, trimmed, without repeats and at most {@link #MAX_TOPICS} — the call-topics
     * report counts calls per tag, and "Internet yo'q" and "internet yo'q " must be one.
     */
    private static List<String> topicsOf(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        Set<String> topics = new LinkedHashSet<>();
        for (Object item : items) {
            if (item == null || item.toString().isBlank()) {
                continue;
            }
            String topic = item.toString().trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            topics.add(topic.length() > MAX_TOPIC_CHARS ? topic.substring(0, MAX_TOPIC_CHARS) : topic);
            if (topics.size() == MAX_TOPICS) {
                break;
            }
        }
        return List.copyOf(topics);
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
        if (reasoningEffort == null || reasoningEffort.isBlank()) {
            return GoogleGenAiThinkingLevel.LOW;
        }
        return switch (reasoningEffort.trim().toLowerCase()) {
            case "high" -> GoogleGenAiThinkingLevel.HIGH;
            default -> GoogleGenAiThinkingLevel.LOW;
        };
    }
}
