package uz.murodjon.robotcallv2.insight.infrastructure.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.insight.application.port.output.InsightClustererPort;
import uz.murodjon.robotcallv2.insight.domain.entity.AgentCallSignals;
import uz.murodjon.robotcallv2.insight.domain.entity.InsightProposal;
import uz.murodjon.robotcallv2.insight.domain.enums.InsightType;
import uz.murodjon.robotcallv2.insight.infrastructure.config.InsightProperties;
import uz.murodjon.robotcallv2.shared.util.PromptText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Asks a model which of a day's signals are the same problem, and what to do about each
 * (UNIVERSAL-OPERATOR-PLAN D.3).
 *
 * <p>The model is given counts and one-line notes, never transcripts, and is told in so many
 * words that it may not invent a cause the numbers do not show. What comes back is checked
 * here rather than trusted: an unknown type, a missing title or a count larger than the
 * window had calls in it drops the proposal, and everything that survives is trimmed to what
 * the column will hold. A proposal is only ever a suggestion to a person, but a suggestion
 * that cites a number nobody measured wastes that person's morning.
 *
 * <p>No model configured — no insights, and a daily job that finishes quietly. The loop is an
 * extra, not something the platform stops without.
 */
@Component
public class SpringAiInsightClustererAdapter implements InsightClustererPort {

    private static final Logger log = LoggerFactory.getLogger(SpringAiInsightClustererAdapter.class);

    /** What the column holds; a longer title is the model writing a paragraph in the wrong field. */
    private static final int MAX_TITLE_CHARS = 200;
    private static final int MAX_TEXT_CHARS = 2000;

    private static final String INSTRUCTIONS = PromptText.text("insight-clusterer.txt");

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final InsightProperties insightProperties;

    public SpringAiInsightClustererAdapter(ObjectProvider<ChatModel> chatModelProvider,
                                           InsightProperties insightProperties) {
        this.chatModelProvider = chatModelProvider;
        this.insightProperties = insightProperties;
    }

    @Override
    public List<InsightProposal> cluster(String agentName, String language, AgentCallSignals signals) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            log.warn("No LLM ChatModel (set GEMINI_API_KEY or OPENAI_API_KEY); no insights will be proposed");
            return List.of();
        }
        try {
            Map<String, Object> answer = ChatClient.create(chatModel).prompt()
                    .options(options().mutate())
                    .system(INSTRUCTIONS)
                    .user(describe(agentName, language, signals))
                    .call()
                    .entity(new MapOutputConverter());
            return proposals(answer, signals);
        } catch (Exception e) {
            log.warn("Insight clustering for agent '{}' failed: {}", agentName, e.getMessage());
            return List.of();
        }
    }

    /** The same model choice the quality judge makes, for the same reason: either vendor may be the one configured. */
    private ChatOptions options() {
        String model = insightProperties.model();
        boolean openAi = chatModelProvider.getIfAvailable() instanceof OpenAiChatModel
                || model.startsWith("gpt-") || model.startsWith("llama") || model.startsWith("qwen");
        return openAi
                ? OpenAiChatOptions.builder().model(model).maxTokens(insightProperties.maxTokens()).build()
                : GoogleGenAiChatOptions.builder().model(model)
                        .maxOutputTokens(insightProperties.maxTokens()).build();
    }

    /** The window as the model reads it: what happened, how often, and what was said about it. */
    private static String describe(String agentName, String language, AgentCallSignals signals) {
        StringBuilder sb = new StringBuilder();
        sb.append("AGENT: ").append(agentName).append('\n');
        sb.append("AGENT LANGUAGE: ").append(language != null ? language : "uz-UZ").append('\n');
        sb.append("CALLS (last 24 hours): ").append(signals.calls()).append('\n');
        if (signals.avgScore() != null) {
            sb.append("AVERAGE QUALITY SCORE: ").append(signals.avgScore()).append(" / 100\n");
        }
        sb.append("SIGNALS (with number of calls):\n")
                .append("- rule violation (said something it should not have): ")
                .append(signals.guardrailViolations()).append('\n')
                .append("- unanswered question: ").append(signals.unansweredQuestions()).append('\n')
                .append("- misused a tool: ").append(signals.toolMisuses()).append('\n')
                .append("- wrong language: ").append(signals.languageMismatches()).append('\n')
                .append("- talked over the client: ").append(signals.talkedOverClient()).append('\n')
                .append("- calls with a tool error: ").append(signals.toolErrorCalls()).append('\n')
                .append("- clients who called again within 24 hours: ").append(signals.repeatCalls()).append('\n')
                .append("- transferred to an operator: ").append(signals.transfers()).append('\n')
                .append("- client said \"nima?\", \"qaytaring\" (total): ").append(signals.repairs()).append('\n')
                .append("- re-prompts after silence (total): ").append(signals.noInputPrompts()).append('\n')
                .append("- low speech-recognition confidence utterances (total): ").append(signals.lowConfidence()).append('\n');
        appendLines(sb, "QUALITY JUDGE NOTES", signals.judgeNotes());
        appendLines(sb, "SUPERVISOR \"AGENT MADE A MISTAKE\" REASONS", signals.reviewerNotes());
        return sb.toString();
    }

    private static void appendLines(StringBuilder sb, String heading, List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        sb.append(heading).append(":\n");
        for (String line : lines) {
            sb.append("- ").append(line).append('\n');
        }
    }

    @SuppressWarnings("unchecked")
    private List<InsightProposal> proposals(Map<String, Object> answer, AgentCallSignals signals) {
        if (answer == null || !(answer.get("insights") instanceof List<?> raw)) {
            return List.of();
        }
        List<InsightProposal> proposals = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof Map<?, ?> fields) {
                InsightProposal proposal = proposal((Map<String, Object>) fields, signals);
                if (proposal != null) {
                    proposals.add(proposal);
                }
            }
        }
        return proposals;
    }

    private static InsightProposal proposal(Map<String, Object> fields, AgentCallSignals signals) {
        InsightType type = typeOf(text(fields.get("type")));
        String title = trim(text(fields.get("title")), MAX_TITLE_CHARS);
        if (type == null || title.isEmpty()) {
            return null;
        }
        int callCount = Math.min(number(fields.get("callCount")), signals.calls());
        return new InsightProposal(type, title, trim(text(fields.get("detail")), MAX_TEXT_CHARS),
                trim(text(fields.get("suggestion")), MAX_TEXT_CHARS), callCount, signals.sampleCalls());
    }

    private static InsightType typeOf(String value) {
        try {
            return InsightType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static int number(Object value) {
        return value instanceof Number n ? Math.max(n.intValue(), 0) : 0;
    }

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
