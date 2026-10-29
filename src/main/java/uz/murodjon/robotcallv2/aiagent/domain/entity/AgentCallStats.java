package uz.murodjon.robotcallv2.aiagent.domain.entity;

/**
 * What an agent's most recent finished calls actually cost and how fast they answered —
 * the measured side of the studio's cost / latency / tokens strip.
 *
 * @param calls             finished calls in the sample
 * @param billedSeconds     talk time of those calls that were charged
 * @param billedUzs         what those charged calls cost, in UZS
 * @param typicalLatencyMs  median of the calls' average turn latency; null when none was measured
 * @param slowLatencyMs     90th percentile of the same
 * @param lowTokensPerTurn  10th percentile of tokens (prompt + completion) per turn; null when unknown
 * @param highTokensPerTurn 90th percentile of the same
 */
public record AgentCallStats(
        int calls,
        long billedSeconds,
        long billedUzs,
        Integer typicalLatencyMs,
        Integer slowLatencyMs,
        Integer lowTokensPerTurn,
        Integer highTokensPerTurn
) {

    public static final AgentCallStats NONE = new AgentCallStats(0, 0, 0, null, null, null, null);

    /** Charged cost of a minute of talk, or null when none of the calls has been charged yet. */
    public Long costPerMinuteUzs() {
        return billedSeconds > 0 ? Math.round(billedUzs * 60.0 / billedSeconds) : null;
    }

    public boolean hasTokens() {
        return lowTokensPerTurn != null && highTokensPerTurn != null;
    }
}
