package uz.murodjon.robotcallv2.aiagent.application.port.output;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentCallStats;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentCallStats;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentFilter;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface AiAgentRepository {

    long create(AiAgent agent);

    /**
     * Writes the editable fields of {@code agent}, only while the stored row is still at
     * {@code agent.version()}.
     *
     * @return {@code false} when someone saved the agent after it was read — nothing is
     *         written; {@code true} otherwise, including for an agent that no longer exists
     */
    boolean update(long companyId, long id, AiAgent agent);

    AiAgent findByCompanyIdAndId(long companyId, long id);

    List<AiAgent> findAll(long companyId, AiAgentFilter filter);

    long count(long companyId, AiAgentFilter filter);

    Map<Long, String> findNamesByIds(Collection<Long> ids);

    void delete(long companyId, long id);

    /** How many campaigns and inbound routes still point at this agent — the delete guard. */
    long countCampaignsUsing(long id);

    long countInboundRoutesUsing(long id);

    /** Whether {@code queueId} is one of the company's call queues — the transfer queue an agent may name. */
    boolean existsCallQueueByCompanyIdAndId(long companyId, long queueId);

    /** Cost, latency and tokens of the agent's last {@code limit} finished calls; {@link AgentCallStats#NONE} when it has none. */
    AgentCallStats findRecentCallStats(long companyId, long agentId, int limit);
}
