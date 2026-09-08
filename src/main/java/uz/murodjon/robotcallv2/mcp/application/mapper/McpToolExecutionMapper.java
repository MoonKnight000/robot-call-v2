package uz.murodjon.robotcallv2.mcp.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallAttemptEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.mcp.domain.entity.McpToolExecution;
import uz.murodjon.robotcallv2.mcp.infrastructure.persistence.entity.McpConnectionEntity;
import uz.murodjon.robotcallv2.mcp.infrastructure.persistence.entity.McpToolExecutionEntity;

/**
 * Write-only: the executions are a log the UI reads through report queries, never back
 * through this port, so there is no entity → domain direction here.
 */
@Component
public class McpToolExecutionMapper {

    /** Connection and call are null for a tool run outside a call, or after the connection was deleted. */
    public McpToolExecutionEntity toEntity(McpToolExecution execution, CompanyEntity company,
                                           McpConnectionEntity connection, CallAttemptEntity callAttempt) {
        if (execution == null) {
            return null;
        }
        McpToolExecutionEntity entity = new McpToolExecutionEntity();
        entity.setCompany(company);
        entity.setConnection(connection);
        entity.setCallAttempt(callAttempt);
        entity.setToolName(execution.toolName());
        entity.setStatus(execution.status());
        entity.setLatencyMs(execution.latencyMs());
        entity.setArgsPreview(execution.argsPreview());
        entity.setResultPreview(execution.resultPreview());
        entity.setError(execution.error());
        return entity;
    }
}
