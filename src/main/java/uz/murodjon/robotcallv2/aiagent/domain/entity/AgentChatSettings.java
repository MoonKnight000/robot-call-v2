package uz.murodjon.robotcallv2.aiagent.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * How a chat agent holds a written conversation (widget, Telegram, chat API). Each value is
 * capped by the platform's own limit ({@code voice-agent.chat.*}): an agent may close sooner or
 * hold fewer messages, never more.
 *
 * @param inactivityTimeoutMinutes silence after which the conversation closes; null — the platform's
 * @param maxMessages              messages (person + agent) one conversation may hold; null — the platform's
 * @param endMessage               fixed words added when the conversation ends; null — none
 */
public record AgentChatSettings(
        @Min(1) @Max(1440) Integer inactivityTimeoutMinutes,
        @Min(2) @Max(500) Integer maxMessages,
        @Size(max = 500) String endMessage
) {

    public static final AgentChatSettings NONE = new AgentChatSettings(null, null, null);

    public int resolveInactivityTimeoutMinutes(int platformLimit) {
        return inactivityTimeoutMinutes != null ? Math.min(inactivityTimeoutMinutes, platformLimit) : platformLimit;
    }

    public int resolveMaxMessages(int platformLimit) {
        return maxMessages != null ? Math.min(maxMessages, platformLimit) : platformLimit;
    }
}
