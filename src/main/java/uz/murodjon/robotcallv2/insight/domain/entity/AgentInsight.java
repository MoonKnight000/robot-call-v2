package uz.murodjon.robotcallv2.insight.domain.entity;

import uz.murodjon.robotcallv2.insight.domain.enums.InsightStatus;
import uz.murodjon.robotcallv2.insight.domain.enums.InsightType;

import java.time.Instant;
import java.util.List;

/**
 * One proposed change to an agent, and how far it has got (UNIVERSAL-OPERATOR-PLAN D.3).
 *
 * <p>The loop's unit of work. It is written by the daily job, decided by a person, and — when
 * the decision was to accept a prompt change — settled by the evaluation run named here: the
 * agent's own test cases are what stands between a suggestion and the calls it would shape.
 *
 * @param suggestion     the change accepting it makes; null for a finding that only names a problem
 * @param decisionReason why it was rejected, or what the evaluation said when it was accepted
 * @param decidedBy      the user who decided; null when the gate decided
 * @param evalRunId      the run the decision is waiting on, or the one it was settled by
 */
public record AgentInsight(
        Long id,
        long companyId,
        long aiAgentId,
        InsightType type,
        String title,
        String detail,
        String suggestion,
        int callCount,
        List<Long> sampleCalls,
        InsightStatus status,
        String decisionReason,
        Long decidedBy,
        Instant decidedAt,
        Long evalRunId,
        Instant createdAt
) {

    public AgentInsight {
        sampleCalls = sampleCalls == null ? List.of() : List.copyOf(sampleCalls);
    }

    /** A proposal about to be written: the adapter assigns the id and the timestamp. */
    public static AgentInsight proposed(long companyId, long aiAgentId, InsightProposal proposal) {
        return new AgentInsight(null, companyId, aiAgentId, proposal.type(), proposal.title(),
                proposal.detail(), proposal.suggestion(), proposal.callCount(), proposal.sampleCalls(),
                InsightStatus.PROPOSED, null, null, null, null, null);
    }

    /** Accepted, and now waiting on {@code runId} to say whether the change may stay. */
    public AgentInsight evaluating(long runId, Long userId, Instant at) {
        return new AgentInsight(id, companyId, aiAgentId, type, title, detail, suggestion, callCount,
                sampleCalls, InsightStatus.EVALUATING, null, userId, at, runId, createdAt);
    }

    public AgentInsight accepted(String reason, Long userId, Instant at) {
        return new AgentInsight(id, companyId, aiAgentId, type, title, detail, suggestion, callCount,
                sampleCalls, InsightStatus.ACCEPTED, reason, userId, at, evalRunId, createdAt);
    }

    public AgentInsight rejected(String reason, Long userId, Instant at) {
        return new AgentInsight(id, companyId, aiAgentId, type, title, detail, suggestion, callCount,
                sampleCalls, InsightStatus.REJECTED, reason, userId, at, evalRunId, createdAt);
    }
}
