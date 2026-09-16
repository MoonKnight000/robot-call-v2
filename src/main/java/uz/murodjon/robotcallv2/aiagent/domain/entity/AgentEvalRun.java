package uz.murodjon.robotcallv2.aiagent.domain.entity;

import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentEvalRunStatus;

import java.time.Instant;

/**
 * One evaluation of an agent against all its test cases (NEXT-PLAN A.2).
 *
 * @param agentVersion the published version evaluated, or null for the draft
 * @param avgQaScore   the judge's mean score over the cases it scored, or null
 * @param error        why the run broke off, when it did
 */
public record AgentEvalRun(
        Long id,
        long companyId,
        long agentId,
        Integer agentVersion,
        AgentEvalRunStatus status,
        int total,
        int passed,
        Integer avgQaScore,
        String error,
        Instant startedAt,
        Instant finishedAt
) {

    public static AgentEvalRun starting(long companyId, long agentId, Integer agentVersion, int total,
                                        Instant startedAt) {
        return new AgentEvalRun(null, companyId, agentId, agentVersion, AgentEvalRunStatus.RUNNING,
                total, 0, null, null, startedAt, null);
    }
}
