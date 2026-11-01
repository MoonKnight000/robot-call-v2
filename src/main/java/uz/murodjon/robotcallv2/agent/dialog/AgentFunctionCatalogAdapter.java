package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AgentFunctionCatalogPort;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentIntegrationTool;
import uz.murodjon.robotcallv2.integration.application.dto.CrmToolRow;
import uz.murodjon.robotcallv2.integration.application.port.input.IntegrationToolUseCase;
import uz.murodjon.robotcallv2.operator.infrastructure.config.OperatorProperties;
import uz.murodjon.robotcallv2.tool.application.dto.ToolRow;
import uz.murodjon.robotcallv2.tool.application.port.input.ToolUseCase;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plugs the agent's function list into the tools, integrations and operator around it. The tool feature
 * itself depends on the agent feature, and so does the integration feature (through tools), so both use
 * cases are looked up when needed, not injected.
 */
@Component
class AgentFunctionCatalogAdapter implements AgentFunctionCatalogPort {

    private final ObjectProvider<ToolUseCase> toolUseCaseProvider;
    private final ObjectProvider<IntegrationToolUseCase> integrationToolUseCaseProvider;
    private final OperatorProperties operatorProperties;

    AgentFunctionCatalogAdapter(ObjectProvider<ToolUseCase> toolUseCaseProvider,
                                ObjectProvider<IntegrationToolUseCase> integrationToolUseCaseProvider,
                                OperatorProperties operatorProperties) {
        this.toolUseCaseProvider = toolUseCaseProvider;
        this.integrationToolUseCaseProvider = integrationToolUseCaseProvider;
        this.operatorProperties = operatorProperties;
    }

    @Override
    public Map<Long, String> findToolNames(long companyId, Collection<Long> toolIds) {
        Map<Long, String> names = new LinkedHashMap<>();
        for (ToolRow tool : toolUseCaseProvider.getObject().findTools(companyId)) {
            if (toolIds.contains(tool.id())) {
                names.put(tool.id(), HttpToolExecutor.sanitizeToolName(tool.name()));
            }
        }
        return names;
    }

    @Override
    public Map<Long, String> findAttachedToolNames(long companyId, long agentId) {
        Map<Long, String> names = new LinkedHashMap<>();
        for (Tool tool : toolUseCaseProvider.getObject().findAgentToolDefinitions(companyId, agentId)) {
            names.put(tool.id(), HttpToolExecutor.sanitizeToolName(tool.name()));
        }
        return names;
    }

    @Override
    public void syncAttachedTools(long companyId, long agentId, Collection<Long> toolIds) {
        ToolUseCase toolUseCase = toolUseCaseProvider.getObject();
        Set<Long> attached = new HashSet<>();
        for (Tool tool : toolUseCase.findAgentToolDefinitions(companyId, agentId)) {
            attached.add(tool.id());
        }
        // A tool deleted since a restored list named it is simply not attached, instead of failing the restore.
        Set<Long> existing = new HashSet<>(findToolNames(companyId, toolIds).keySet());
        for (Long toolId : existing) {
            if (!attached.contains(toolId)) {
                toolUseCase.attachTool(companyId, agentId, toolId);
            }
        }
        for (Long toolId : attached) {
            if (!existing.contains(toolId)) {
                toolUseCase.detachTool(companyId, agentId, toolId);
            }
        }
    }

    @Override
    public List<AgentIntegrationTool> findEnabledIntegrationTools(long companyId) {
        // A tool the company's CRM grants do not cover is never offered, whatever its switch says.
        return integrationToolUseCaseProvider.getObject().findTools(companyId).stream()
                .filter(row -> row.enabled() && row.granted())
                .map((CrmToolRow row) -> new AgentIntegrationTool(row.tool(), row.toolName()))
                .toList();
    }

    @Override
    public boolean hasGlobalOperator() {
        return TransferTargets.findGlobalOperatorEndpoint(operatorProperties) != null;
    }
}
