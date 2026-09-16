package uz.murodjon.robotcallv2.tool.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolExecution;
import uz.murodjon.robotcallv2.tool.infrastructure.persistence.entity.ToolExecutionEntity;

/** Write-only, like the log it maps: nothing reads a row back through the port yet. */
@Component
public class ToolExecutionEntityMapper {

    public ToolExecutionEntity toToolExecutionEntity(ToolExecution execution) {
        if (execution == null) {
            return null;
        }
        ToolExecutionEntity entity = new ToolExecutionEntity();
        entity.setCompanyId(execution.companyId());
        entity.setToolId(execution.toolId());
        entity.setCallAttemptId(execution.callAttemptId());
        entity.setToolName(execution.toolName());
        entity.setStatus(execution.status());
        entity.setHttpStatus(execution.httpStatus());
        entity.setLatencyMs(execution.latencyMs());
        entity.setArgsHash(execution.argsHash());
        entity.setError(execution.error());
        return entity;
    }
}
