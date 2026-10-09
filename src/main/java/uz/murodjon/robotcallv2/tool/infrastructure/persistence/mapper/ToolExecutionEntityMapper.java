package uz.murodjon.robotcallv2.tool.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolExecution;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolExecutionStats;
import uz.murodjon.robotcallv2.tool.infrastructure.persistence.entity.ToolExecutionEntity;
import uz.murodjon.robotcallv2.tool.infrastructure.persistence.repository.ToolExecutionStatsProjection;

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

    public ToolExecution toToolExecution(ToolExecutionEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ToolExecution(
                entity.getId(),
                entity.getCompanyId(),
                entity.getToolId(),
                entity.getCallAttemptId(),
                entity.getToolName(),
                entity.getStatus(),
                entity.getHttpStatus(),
                entity.getLatencyMs(),
                entity.getArgsHash(),
                entity.getError(),
                entity.getCreatedAt());
    }

    public ToolExecutionStats toToolExecutionStats(ToolExecutionStatsProjection projection) {
        return new ToolExecutionStats(
                projection.getToolId(),
                projection.getToolName(),
                projection.getTotal(),
                projection.getOk(),
                projection.getHttpError(),
                projection.getFailed(),
                (int) Math.round(projection.getAvgLatencyMs()),
                (int) Math.round(projection.getP95LatencyMs()));
    }
}
