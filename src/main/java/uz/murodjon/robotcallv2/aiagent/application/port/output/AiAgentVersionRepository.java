package uz.murodjon.robotcallv2.aiagent.application.port.output;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentVersionStats;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentVersion;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface AiAgentVersionRepository {

    /**
     * Stores {@code version} as the agent's next number and archives the version that was
     * published before it. Returns the stored row, number and timestamp filled in.
     */
    AiAgentVersion publish(AiAgentVersion version);

    /** The agent's live version, or null when it was never published. */
    AiAgentVersion findPublished(long companyId, long agentId);

    /** Live versions of several agents at once — one query for a list page. */
    List<AiAgentVersion> findPublishedByAgentIds(long companyId, Collection<Long> agentIds);

    AiAgentVersion findByAgentIdAndVersion(long companyId, long agentId, int version);

    /** Every version of the agent, newest first. */
    List<AiAgentVersion> findByAgentId(long companyId, long agentId);

    /** Call results per published version (NEXT-PLAN A.1); versions with no calls are absent. */
    List<AgentVersionStats> findCallStats(long companyId, long agentId);

    /** The same, over calls started at or after {@code since} — a rollout compares its two versions from its start. */
    List<AgentVersionStats> findCallStatsSince(long companyId, long agentId, Instant since);

    /** Stores {@code version} as the agent's next number with status {@code CANDIDATE}; the live version stays. */
    AiAgentVersion createCandidate(AiAgentVersion version);

    /** The candidate becomes the live version and the live one is archived. */
    void promoteCandidate(long companyId, long agentId);

    /** The candidate is archived; the live version is untouched. */
    void archiveCandidate(long companyId, long agentId);
}
