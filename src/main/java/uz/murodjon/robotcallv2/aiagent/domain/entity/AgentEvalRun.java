package uz.murodjon.robotcallv2.aiagent.domain.entity;

import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentEvalMode;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentEvalRunStatus;

import java.time.Instant;

/**
 * One evaluation of an agent against all its test cases (NEXT-PLAN A.2).
 *
 * @param agentVersion the published version evaluated, or null for the draft
 * @param mode         whether the cases were played as text or as audio
 * @param configHash   fingerprint of the agent configuration evaluated ({@code AiAgentSnapshot#fingerprint});
 *                     what lets publishing tell whether the draft has changed since
 * @param avgQaScore   the judge's mean score over the cases it scored, or null
 * @param error        why the run broke off, when it did
 * @param repeat       how many times each case was run; a case passes only when every attempt did
 */
public record AgentEvalRun(
        Long id,
        long companyId,
        long agentId,
        Integer agentVersion,
        AgentEvalMode mode,
        String configHash,
        AgentEvalRunStatus status,
        int total,
        int passed,
        Integer avgQaScore,
        String error,
        Instant startedAt,
        Instant finishedAt,
        int repeat
) {

    /** @param total cases times {@code repeat} — every attempt is counted */
    public static AgentEvalRun starting(long companyId, long agentId, Integer agentVersion, AgentEvalMode mode,
                                        String configHash, int total, int repeat, Instant startedAt) {
        return new AgentEvalRun(null, companyId, agentId, agentVersion, mode, configHash, AgentEvalRunStatus.RUNNING,
                total, 0, null, null, startedAt, null, repeat);
    }
}
