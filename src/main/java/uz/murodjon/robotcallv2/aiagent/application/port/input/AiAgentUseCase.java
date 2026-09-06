package uz.murodjon.robotcallv2.aiagent.application.port.input;

import uz.murodjon.robotcallv2.aiagent.application.dto.AiAgentRow;
import uz.murodjon.robotcallv2.aiagent.application.dto.CreateAiAgentRequest;
import uz.murodjon.robotcallv2.aiagent.application.dto.UpdateAiAgentRequest;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.Collection;
import java.util.Map;

public interface AiAgentUseCase {

    AiAgentRow createAgent(long companyId, CreateAiAgentRequest request);

    AiAgentRow updateAgent(long companyId, long id, UpdateAiAgentRequest request);

    AiAgentRow findAgentRow(long companyId, long id);

    PageableData<AiAgentRow> filterAgents(long companyId, AiAgentFilter filter);

    void deleteAgent(long companyId, long id);

    /** The agent a call runs under — also reached from the dialer sweep and the ARI event loop. */
    AiAgent requireAgent(long companyId, long id);

    Map<Long, String> findNamesByIds(Collection<Long> ids);
}
