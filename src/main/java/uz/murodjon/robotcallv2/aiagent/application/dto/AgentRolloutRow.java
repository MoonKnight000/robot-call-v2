package uz.murodjon.robotcallv2.aiagent.application.dto;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentRollout;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentVersionStats;

import java.time.Instant;
import java.util.List;

/**
 * A running rollout and how its two versions have done since it started — the same window for
 * both, so the comparison is fair.
 *
 * @param liveVersion the published version the rest of the traffic runs on
 */
public record AgentRolloutRow(
        int candidateVersion,
        int liveVersion,
        int trafficPercent,
        List<String> stagingPhones,
        Instant startedAt,
        Long startedBy,
        AgentRolloutResult live,
        AgentRolloutResult candidate
) {

    public static AgentRolloutRow of(AgentRollout rollout, int liveVersion, AgentVersionStats liveStats,
                                     AgentVersionStats candidateStats) {
        return new AgentRolloutRow(rollout.candidateVersion(), liveVersion, rollout.trafficPercent(),
                rollout.stagingPhones(), rollout.startedAt(), rollout.startedBy(),
                AgentRolloutResult.of(liveStats), AgentRolloutResult.of(candidateStats));
    }
}
