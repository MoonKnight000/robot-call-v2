package uz.murodjon.robotcallv2.aiagent.application.dto;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentVersionStats;

/**
 * One side of a rollout since it started.
 *
 * @param conversionRate conversions among calls, in percent; null with no calls yet
 */
public record AgentRolloutResult(
        int version,
        long calls,
        long conversions,
        Double conversionRate
) {

    public static AgentRolloutResult of(AgentVersionStats stats) {
        return new AgentRolloutResult(stats.version(), stats.calls(), stats.conversions(),
                stats.calls() == 0 ? null : stats.conversions() * 100.0 / stats.calls());
    }
}
