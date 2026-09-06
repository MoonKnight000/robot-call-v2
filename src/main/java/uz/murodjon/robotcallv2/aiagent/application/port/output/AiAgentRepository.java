package uz.murodjon.robotcallv2.aiagent.application.port.output;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentFilter;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface AiAgentRepository {

    long create(AiAgent agent);

    void update(long companyId, long id, AiAgent agent);

    AiAgent findByCompanyIdAndId(long companyId, long id);

    List<AiAgent> findAll(long companyId, AiAgentFilter filter);

    long count(long companyId, AiAgentFilter filter);

    Map<Long, String> findNamesByIds(Collection<Long> ids);

    void delete(long companyId, long id);

    /** How many campaigns and inbound routes still point at this agent — the delete guard. */
    long countCampaignsUsing(long id);

    long countInboundRoutesUsing(long id);
}
