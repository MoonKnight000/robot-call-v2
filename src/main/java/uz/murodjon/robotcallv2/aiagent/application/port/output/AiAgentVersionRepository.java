package uz.murodjon.robotcallv2.aiagent.application.port.output;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentVersionStats;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentVersion;

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
}
