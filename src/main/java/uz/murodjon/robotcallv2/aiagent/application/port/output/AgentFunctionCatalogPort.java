package uz.murodjon.robotcallv2.aiagent.application.port.output;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentIntegrationTool;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * What the agent's function list needs from the features around it — the company's tools and integration
 * tools and the platform's operator — which this feature may not call itself.
 */
public interface AgentFunctionCatalogPort {

    /**
     * The model-facing names of those of {@code toolIds} that are tools of {@code companyId}; an id that is
     * not the company's is absent from the answer.
     */
    Map<Long, String> findToolNames(long companyId, Collection<Long> toolIds);

    /** The tools attached to the agent: id and model-facing name, in attachment order. */
    Map<Long, String> findAttachedToolNames(long companyId, long agentId);

    /** Makes the agent's attached tools exactly {@code toolIds}: attaches the missing, detaches the rest. */
    void syncAttachedTools(long companyId, long agentId, Collection<Long> toolIds);

    /**
     * The integration tools the company has on, whether or not an agent switched them off.
     */
    List<AgentIntegrationTool> findEnabledIntegrationTools(long companyId);

    /** Whether the platform has a global operator to hand a call to. */
    boolean hasGlobalOperator();
}
