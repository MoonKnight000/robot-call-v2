package uz.murodjon.robotcallv2.aiagent.domain.entity;

/**
 * The ceilings a call runs under. Every one of them is optional, and {@code null} means
 * the deployment's own configured limit applies rather than "no limit" — the one
 * exception is the widget path, which insists on a ceiling of its own because the caller
 * there is a stranger on the open internet ({@code WidgetCallGate}).
 *
 * @param maxConversationDurationSeconds   hard stop on the whole call
 * @param silenceEndCallTimeoutSeconds     silence after which the agent gives up on the caller
 * @param turnTimeoutSeconds               how long one turn may take before it is abandoned
 * @param concurrentCallsLimit             calls this agent may have in flight at once
 * @param dailyCallsLimit                  calls this agent may take in a day
 */
public record AiAgentLimits(
        Integer maxConversationDurationSeconds,
        Integer silenceEndCallTimeoutSeconds,
        Integer turnTimeoutSeconds,
        Integer concurrentCallsLimit,
        Integer dailyCallsLimit
) {
    /** No limit of the agent's own on anything; the deployment's configuration decides. */
    public static final AiAgentLimits NONE = new AiAgentLimits(null, null, null, null, null);
}
