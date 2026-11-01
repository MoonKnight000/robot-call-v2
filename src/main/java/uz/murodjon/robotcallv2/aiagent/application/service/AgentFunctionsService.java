package uz.murodjon.robotcallv2.aiagent.application.service;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AgentFunctionCatalogPort;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AiAgentRepository;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunction;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunctionSources;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentIntegrationTool;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentFunctionType;
import uz.murodjon.robotcallv2.aiagent.domain.service.AgentFunctionDefaults;
import uz.murodjon.robotcallv2.aiagent.domain.service.AgentFunctionValidator;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The agent's {@code functions} list as the service needs it: a saved list checked against the company's
 * tools and agents and kept in step with the tools attached to the agent, and the list an agent that never
 * saved one is shown with ({@link AgentFunctionDefaults}). The shape rules are
 * {@link AgentFunctionValidator}'s; this is what needs a lookup.
 */
@Component
class AgentFunctionsService {

    private final AiAgentRepository repository;
    private final AgentFunctionCatalogPort catalogPort;

    AgentFunctionsService(AiAgentRepository repository, AgentFunctionCatalogPort catalogPort) {
        this.repository = repository;
        this.catalogPort = catalogPort;
    }

    /**
     * {@code requested} as it will be stored: names fixed, a CUSTOM function named after its tool, and every
     * tool and agent it points at proven to be the company's ({@code AI_AGENT_FUNCTION_TOOL_NOT_FOUND}, 400).
     * Shape is checked later by {@code AiAgentValidator}, with the rest of the agent.
     */
    List<AgentFunction> prepare(long companyId, long agentId, List<AgentFunction> requested) {
        List<AgentFunction> normalized = AgentFunctionValidator.normalize(requested);
        Set<Long> toolIds = new LinkedHashSet<>();
        for (AgentFunction function : normalized) {
            if (function.type() == AgentFunctionType.CUSTOM && function.toolId() != null) {
                toolIds.add(function.toolId());
            }
        }
        Map<Long, String> toolNames = toolIds.isEmpty() ? Map.of() : catalogPort.findToolNames(companyId, toolIds);
        List<AgentFunction> prepared = new ArrayList<>();
        for (AgentFunction function : normalized) {
            if (function.type() == AgentFunctionType.CUSTOM && function.toolId() != null) {
                String name = toolNames.get(function.toolId());
                if (name == null) {
                    throw new ValidationException(ErrorCode.AI_AGENT_FUNCTION_TOOL_NOT_FOUND,
                            function.id(), "toolId", function.toolId());
                }
                function = function.toBuilder().name(name).build();
            }
            if (function.type() == AgentFunctionType.AGENT_TRANSFER && function.agentTransfer() != null
                    && function.agentTransfer().agentId() != null) {
                requireTransferTarget(companyId, agentId, function);
            }
            prepared.add(function);
        }
        return prepared;
    }

    /** Makes the agent's attached tools the CUSTOM tools of {@code functions}. */
    void syncTools(long companyId, long agentId, List<AgentFunction> functions) {
        Set<Long> toolIds = new LinkedHashSet<>();
        for (AgentFunction function : functions) {
            if (function.type() == AgentFunctionType.CUSTOM && function.toolId() != null) {
                toolIds.add(function.toolId());
            }
        }
        catalogPort.syncAttachedTools(companyId, agentId, toolIds);
    }

    /** The list {@code agent} is shown with: its own, else the one derived from its older settings. */
    List<AgentFunction> resolve(long companyId, AiAgent agent) {
        return resolveAll(companyId, List.of(agent)).getFirst();
    }

    /**
     * {@link #resolve} for several agents of one company, in the same order. What is the same for all of them — the
     * company's integration tools, the global operator — is asked once, and only if some agent needs it.
     */
    List<List<AgentFunction>> resolveAll(long companyId, List<AiAgent> agents) {
        List<List<AgentFunction>> resolved = new ArrayList<>();
        List<AgentIntegrationTool> integrationTools = null;
        Boolean globalOperator = null;
        for (AiAgent agent : agents) {
            if (agent.functions() != null) {
                resolved.add(agent.functions());
                continue;
            }
            if (integrationTools == null) {
                integrationTools = catalogPort.findEnabledIntegrationTools(companyId);
                globalOperator = catalogPort.hasGlobalOperator();
            }
            AgentFunctionSources sources = new AgentFunctionSources(
                    catalogPort.findAttachedToolNames(companyId, agent.id()), integrationTools, globalOperator);
            resolved.add(AgentFunctionDefaults.resolve(agent, sources));
        }
        return resolved;
    }

    private void requireTransferTarget(long companyId, long agentId, AgentFunction function) {
        long targetId = function.agentTransfer().agentId();
        if (targetId == agentId) {
            throw new ValidationException(ErrorCode.AI_AGENT_FUNCTION_INVALID, function.id(), "AGENT_TRANSFER_TO_SELF");
        }
        if (repository.findByCompanyIdAndId(companyId, targetId) == null) {
            throw new ValidationException(ErrorCode.AI_AGENT_FUNCTION_TOOL_NOT_FOUND,
                    function.id(), "agentTransfer.agentId", targetId);
        }
    }
}
