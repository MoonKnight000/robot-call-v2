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

    AiAgentRow createAgent(CreateAiAgentRequest request);

    AiAgentRow updateAgent(long id, UpdateAiAgentRequest request);

    AiAgentRow findAgentRow(long id);

    PageableData<AiAgentRow> filterAgents(AiAgentFilter filter);

    void deleteAgent(long id);

    /**
     * The agent a call runs under. Takes the company explicitly rather than reading the
     * request's, because its callers are the dialer sweep and the ARI event loop — threads
     * that serve every tenant and have no current company of their own.
     */
    AiAgent requireAgent(long companyId, long id);

    Map<Long, String> findNamesByIds(Collection<Long> ids);
}
