package uz.murodjon.robotcallv2.tool.application.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.tool.application.dto.CreateToolRequest;
import uz.murodjon.robotcallv2.tool.application.dto.OpenApiOperationRow;
import uz.murodjon.robotcallv2.tool.application.dto.ToolRow;
import uz.murodjon.robotcallv2.tool.domain.entity.OpenApiOperation;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;

import java.time.Instant;

@Component
public class ToolMapper {

    public Tool fromCreateRequest(long companyId, CreateToolRequest request) {
        Instant now = Instant.now();
        return new Tool(
                null,
                companyId,
                request.name(),
                request.description(),
                request.apiUrl(),
                request.apiMethod(),
                request.apiHeaders(),
                request.apiBody(),
                request.apiQueryParams(),
                request.apiPathParams(),
                request.responseTimeoutSecs(),
                request.dynamicVariables(),
                request.disableInterruptions() != null && request.disableInterruptions(),
                request.forcePreToolSpeech() == null || request.forcePreToolSpeech(),
                request.preToolSpeech(),
                now,
                now,
                request.responseMapping(),
                request.retryAttempts() != null ? request.retryAttempts() : 0,
                request.maxCallsPerCall() != null ? request.maxCallsPerCall() : Tool.DEFAULT_MAX_CALLS_PER_CALL,
                request.bodySchema(),
                request.effect(),
                request.requiresConfirmation(),
                request.readbackTemplate(),
                request.auth()
        );
    }

    /** An imported tool (UNIVERSAL-OPERATOR-PLAN B.2), as the create request it is saved through. */
    public CreateToolRequest toCreateRequest(Tool tool) {
        return new CreateToolRequest(tool.name(), tool.description(), tool.apiUrl(), tool.apiMethod(),
                tool.apiHeaders(), tool.apiBody(), tool.apiQueryParams(), tool.apiPathParams(),
                tool.responseTimeoutSecs(), tool.dynamicVariables(), tool.disableInterruptions(),
                tool.forcePreToolSpeech(), tool.preToolSpeech(), tool.responseMapping(), tool.retryAttempts(),
                tool.maxCallsPerCall(), tool.bodySchema(), tool.effect(), tool.requiresConfirmation(),
                tool.readbackTemplate(), tool.auth());
    }

    public OpenApiOperationRow toOperationRow(OpenApiOperation operation) {
        return new OpenApiOperationRow(operation.operationId(), operation.method(), operation.path(),
                operation.summary(), operation.tool() != null ? operation.tool().name() : null,
                operation.tool() != null, operation.unsupportedReason(), operation.secretKeys());
    }

    public ToolRow toRow(Tool tool) {
        if (tool == null) {
            return null;
        }
        return new ToolRow(
                tool.id(),
                tool.companyId(),
                tool.name(),
                tool.description(),
                tool.apiUrl(),
                tool.apiMethod(),
                tool.apiHeaders(),
                tool.apiBody(),
                tool.apiQueryParams(),
                tool.apiPathParams(),
                tool.responseTimeoutSecs(),
                tool.dynamicVariables(),
                tool.disableInterruptions(),
                tool.forcePreToolSpeech(),
                tool.preToolSpeech(),
                tool.createdAt(),
                tool.updatedAt(),
                tool.responseMapping(),
                tool.retryAttempts(),
                tool.maxCallsPerCall(),
                tool.bodySchema(),
                tool.effect(),
                tool.requiresConfirmation(),
                tool.readbackTemplate(),
                tool.auth()
        );
    }
}
