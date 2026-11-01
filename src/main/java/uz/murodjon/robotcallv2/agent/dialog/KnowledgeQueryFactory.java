package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the {@link KnowledgeQuery} of a call: what the caller just said, the agent's focus
 * text and, in a cascade call, the exchange just before it.
 *
 * <p>A caller's follow-up ("narxi?", "va muddati?") means nothing alone; embedded together with
 * the line it answers it lands on the right passage. Keywords stay on the latest words only.
 */
final class KnowledgeQueryFactory {

    /** Conversation context plus the latest words, in characters; the oldest part is dropped first. */
    static final int MAX_CONTEXT_CHARS = 500;

    private KnowledgeQueryFactory() {
    }

    /**
     * The query for a cascade turn. {@code history} still ends with the caller's latest
     * message; the agent's previous line and the caller message before it come from there.
     */
    static KnowledgeQuery createFromHistory(AiAgent agent, Map<String, Object> facts, List<Message> history,
                                            String latest) {
        List<String> parts = new ArrayList<>();
        String previousAgentLine = null;
        String previousCallerLine = null;
        boolean skippedLatest = false;
        for (int i = history.size() - 1; i >= 0 && previousCallerLine == null; i--) {
            Message message = history.get(i);
            if (message instanceof UserMessage user && !SystemPromptFactory.isSystemNote(user.getText())) {
                if (!skippedLatest) {
                    skippedLatest = true;
                } else if (previousAgentLine != null) {
                    previousCallerLine = user.getText();
                }
            } else if (message instanceof AssistantMessage assistant && previousAgentLine == null
                    && !SystemPromptFactory.isSystemNote(assistant.getText())) {
                previousAgentLine = assistant.getText();
            }
        }
        addIfPresent(parts, previousCallerLine);
        addIfPresent(parts, previousAgentLine);
        addIfPresent(parts, latest);
        return create(agent, facts, latest, joinWithinCap(parts));
    }

    /** The query for a realtime search: the model's own question, no history. */
    static KnowledgeQuery create(AiAgent agent, Map<String, Object> facts, String question) {
        return create(agent, facts, question, question == null ? null : question.strip());
    }

    private static KnowledgeQuery create(AiAgent agent, Map<String, Object> facts, String question, String context) {
        String instruction = agent == null || agent.ragInstruction() == null ? null
                : PromptTemplateEngine.render(agent.ragInstruction(), facts == null ? Map.of() : facts);
        String semanticText = instruction == null || instruction.isBlank() ? context : instruction.strip() + "\n" + context;
        return new KnowledgeQuery(question, semanticText, agent == null ? null : agent.ragMinScore());
    }

    private static void addIfPresent(List<String> parts, String text) {
        if (text != null && !text.isBlank()) {
            parts.add(text.strip());
        }
    }

    private static String joinWithinCap(List<String> parts) {
        int total = parts.stream().mapToInt(String::length).sum() + Math.max(0, parts.size() - 1);
        while (total > MAX_CONTEXT_CHARS && parts.size() > 1) {
            total -= parts.removeFirst().length() + 1;
        }
        String joined = String.join("\n", parts);
        return joined.length() > MAX_CONTEXT_CHARS ? joined.substring(0, MAX_CONTEXT_CHARS) : joined;
    }
}
