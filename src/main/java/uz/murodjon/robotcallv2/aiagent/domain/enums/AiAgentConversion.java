package uz.murodjon.robotcallv2.aiagent.domain.enums;

/**
 * What {@code POST /api/ai-agents/{id}/convert} turns a copy of the agent into:
 * - {@link #SINGLE_PROMPT}: the conversation flow written out as one system prompt.
 * - {@link #CHAT}: the same agent on the text channel.
 */
public enum AiAgentConversion {
    SINGLE_PROMPT,
    CHAT
}
