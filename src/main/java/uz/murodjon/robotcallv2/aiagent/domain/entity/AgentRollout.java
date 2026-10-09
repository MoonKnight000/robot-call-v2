package uz.murodjon.robotcallv2.aiagent.domain.entity;

import java.time.Instant;
import java.util.List;

/**
 * A candidate version running beside the live one: {@code trafficPercent} of callers, and every
 * staging number, get the candidate; the rest stay on the published version. One per agent.
 *
 * @param trafficPercent 0–100; 0 — only the staging numbers reach the candidate
 * @param stagingPhones  test numbers that always get the candidate, normalised
 * @param startedBy      the app_user who started it, null when a system job did
 */
public record AgentRollout(
        long agentId,
        long companyId,
        int candidateVersion,
        int trafficPercent,
        List<String> stagingPhones,
        Instant startedAt,
        Long startedBy
) {

    public AgentRollout {
        stagingPhones = stagingPhones != null ? List.copyOf(stagingPhones) : List.of();
    }

    /** The same rollout with a new split; the candidate and the start stay. */
    public AgentRollout withSplit(int trafficPercent, List<String> stagingPhones) {
        return new AgentRollout(agentId, companyId, candidateVersion, trafficPercent, stagingPhones, startedAt,
                startedBy);
    }
}
