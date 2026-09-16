package uz.murodjon.robotcallv2.chat.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The text channel (UNIVERSAL-OPERATOR-PLAN E.4). Bound from {@code voice-agent.chat.*}.
 *
 * <p>Every one of these is a ceiling rather than a preference. A chat is opened from a page
 * on the open internet, and each message it holds is a model call somebody pays for: the
 * widget key is printed in the customer's own page and the origin header is only a header,
 * so what stands between this and a script is these numbers.
 *
 * @param maxMessagesPerSession messages — the visitor's and the agent's together — one
 *                              conversation may hold. Reached, it is closed rather than trimmed:
 *                              a conversation this long has stopped being the one that was started
 * @param dailySessionsPerAgent conversations one agent will open in a day, counted across
 *                              every instance
 * @param sessionTtlMinutes     silence after which a conversation is over. A visitor who
 *                              comes back to the tab tomorrow starts a new one rather than
 *                              continuing one whose context nobody remembers
 */
@ConfigurationProperties(prefix = "voice-agent.chat")
public record ChatProperties(
        int maxMessagesPerSession,
        int dailySessionsPerAgent,
        int sessionTtlMinutes
) {

    public ChatProperties {
        if (maxMessagesPerSession <= 0) {
            maxMessagesPerSession = 40;
        }
        if (dailySessionsPerAgent <= 0) {
            dailySessionsPerAgent = 200;
        }
        if (sessionTtlMinutes <= 0) {
            sessionTtlMinutes = 120;
        }
    }
}
