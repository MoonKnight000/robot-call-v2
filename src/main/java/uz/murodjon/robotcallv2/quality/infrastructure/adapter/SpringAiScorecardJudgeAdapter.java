package uz.murodjon.robotcallv2.quality.infrastructure.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.dialog.TokenUsage;
import uz.murodjon.robotcallv2.agent.summary.QualityProperties;
import uz.murodjon.robotcallv2.quality.application.port.output.ScorecardJudgePort;
import uz.murodjon.robotcallv2.quality.domain.entity.CallScorecardAnswer;
import uz.murodjon.robotcallv2.quality.domain.entity.ScorecardCriterion;
import uz.murodjon.robotcallv2.quality.domain.entity.ScorecardVerdict;
import uz.murodjon.robotcallv2.quality.domain.enums.CriterionAnswer;
import uz.murodjon.robotcallv2.shared.dialog.Sentiment;
import uz.murodjon.robotcallv2.shared.util.PromptText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Asks a model how an operator call met each of the company's criteria
 * (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md §2.3), with the words it rests each answer on.
 *
 * <p>Runs under the same switch, sample rate and model as the AI call judge
 * ({@code voice-agent.quality.*}). What comes back is checked rather than trusted: a criterion
 * the model skipped or answered with something unknown counts as not applicable — it then
 * neither helps nor hurts the score — and evidence is trimmed to what the column holds.
 */
@Component
public class SpringAiScorecardJudgeAdapter implements ScorecardJudgePort {

    private static final Logger log = LoggerFactory.getLogger(SpringAiScorecardJudgeAdapter.class);

    private static final int MAX_EVIDENCE_CHARS = 500;
    private static final int MAX_TOPICS = 3;
    private static final int MAX_TOPIC_CHARS = 60;
    /** Room for every criterion's answer and quote, which the AI judge's single verdict does not need. */
    private static final int MIN_OUTPUT_TOKENS = 2048;

    private static final String INSTRUCTIONS = PromptText.text("scorecard-judge.txt");

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final QualityProperties qualityProperties;

    public SpringAiScorecardJudgeAdapter(ObjectProvider<ChatModel> chatModelProvider,
                                         QualityProperties qualityProperties) {
        this.chatModelProvider = chatModelProvider;
        this.qualityProperties = qualityProperties;
    }

    @Override
    public ScorecardVerdict findVerdict(List<ScorecardCriterion> criteria, String transcript) {
        if (!qualityProperties.enabled() || !sampled()) {
            return null;
        }
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            log.warn("No LLM ChatModel (set GEMINI_API_KEY or OPENAI_API_KEY); operator calls are not scored");
            return null;
        }
        try {
            ResponseEntity<ChatResponse, Map<String, Object>> answer = ChatClient.create(chatModel).prompt()
                    .options(options().mutate())
                    .system(INSTRUCTIONS)
                    .user(describe(criteria) + "\nTRANSCRIPT:\n" + transcript)
                    .call()
                    .responseEntity(new MapOutputConverter());
            TokenUsage tokens = new TokenUsage();
            tokens.add(answer.response());
            Map<String, Object> raw = answer.entity();
            return new ScorecardVerdict(answers(criteria, raw), sentimentOf(raw), topicsOf(raw),
                    tokens.promptTokens(), tokens.completionTokens(), tokens.cachedTokens());
        } catch (Exception e) {
            log.warn("Operator call scoring failed: {}", e.getMessage());
            return null;
        }
    }

    private boolean sampled() {
        double rate = qualityProperties.sampleRate();
        return rate >= 1.0 || ThreadLocalRandom.current().nextDouble() < rate;
    }

    /** The same model choice the quality judge makes: either vendor may be the one configured. */
    private ChatOptions options() {
        String model = qualityProperties.model();
        int maxTokens = Math.max(qualityProperties.maxTokens(), MIN_OUTPUT_TOKENS);
        boolean openAi = chatModelProvider.getIfAvailable() instanceof OpenAiChatModel
                || model.startsWith("gpt-") || model.startsWith("llama") || model.startsWith("qwen");
        return openAi
                ? OpenAiChatOptions.builder().model(model).maxTokens(maxTokens).build()
                : GoogleGenAiChatOptions.builder().model(model).maxOutputTokens(maxTokens).build();
    }

    private static String describe(List<ScorecardCriterion> criteria) {
        StringBuilder sb = new StringBuilder("CRITERIA:\n");
        for (int i = 0; i < criteria.size(); i++) {
            ScorecardCriterion criterion = criteria.get(i);
            sb.append(i + 1).append(". ").append(criterion.name()).append(" — ").append(criterion.instruction())
                    .append('\n');
        }
        return sb.toString();
    }

    /** One answer per criterion, by its number in the prompt; a missing or unreadable one is not applicable. */
    private static List<CallScorecardAnswer> answers(List<ScorecardCriterion> criteria, Map<String, Object> raw) {
        Map<Integer, Map<?, ?>> byNumber = new HashMap<>();
        if (raw != null && raw.get("answers") instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> fields && fields.get("criterion") instanceof Number number) {
                    byNumber.putIfAbsent(number.intValue(), fields);
                }
            }
        }
        List<CallScorecardAnswer> answers = new ArrayList<>();
        for (int i = 0; i < criteria.size(); i++) {
            Map<?, ?> fields = byNumber.get(i + 1);
            CriterionAnswer answer = fields != null ? answerOf(fields.get("answer")) : CriterionAnswer.NOT_APPLICABLE;
            String evidence = fields != null && answer != CriterionAnswer.NOT_APPLICABLE
                    ? evidenceOf(fields.get("evidence")) : null;
            answers.add(CallScorecardAnswer.of(criteria.get(i), answer, evidence));
        }
        return answers;
    }

    private static CriterionAnswer answerOf(Object value) {
        try {
            return value == null ? CriterionAnswer.NOT_APPLICABLE
                    : CriterionAnswer.valueOf(value.toString().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return CriterionAnswer.NOT_APPLICABLE;
        }
    }

    /** Null when the model left it out or said something unknown — the call is then flagged on its score alone. */
    private static Sentiment sentimentOf(Map<String, Object> raw) {
        Object value = raw == null ? null : raw.get("sentiment");
        try {
            return value == null ? null : Sentiment.valueOf(value.toString().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Lowercased, trimmed to what the column holds, without repeats — the reports count calls
     * per tag, and "Nasiya" and "nasiya " must be one.
     */
    private static List<String> topicsOf(Map<String, Object> raw) {
        if (raw == null || !(raw.get("topics") instanceof List<?> items)) {
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

    private static String evidenceOf(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        String text = value.toString().trim();
        return text.length() > MAX_EVIDENCE_CHARS ? text.substring(0, MAX_EVIDENCE_CHARS) : text;
    }
}
