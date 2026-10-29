package uz.murodjon.robotcallv2.aiagent.application.dto;

/**
 * The studio's cost / latency / tokens strip for one agent.
 *
 * @param costPerMinuteUzs what a minute of talk costs, in UZS
 * @param costEstimated    true when no call of the agent has been charged yet and the cost is
 *                         priced from the current rates and the agent's prompt
 * @param latencyFromMs    median turn latency of recent calls; null before any measured call
 * @param latencyToMs      90th percentile of the same; null before any measured call
 * @param tokensFrom       tokens per turn, low end
 * @param tokensTo         tokens per turn, high end
 * @param tokensEstimated  true when the token range comes from the prompt rather than calls
 * @param sampleCalls      recent finished calls the measured values come from
 */
public record AiAgentEstimateResponse(
        long costPerMinuteUzs,
        boolean costEstimated,
        Integer latencyFromMs,
        Integer latencyToMs,
        int tokensFrom,
        int tokensTo,
        boolean tokensEstimated,
        int sampleCalls
) {
}
