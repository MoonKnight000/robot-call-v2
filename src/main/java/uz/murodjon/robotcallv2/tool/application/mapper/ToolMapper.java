package uz.murodjon.robotcallv2.tool.application.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.tool.application.dto.CreateToolRequest;
import uz.murodjon.robotcallv2.tool.application.dto.OpenApiOperationRow;
import uz.murodjon.robotcallv2.tool.application.dto.ToolHttpConfigDto;
import uz.murodjon.robotcallv2.tool.application.dto.ToolRow;
import uz.murodjon.robotcallv2.tool.application.dto.ToolSpeechDto;
import uz.murodjon.robotcallv2.tool.domain.entity.OpenApiOperation;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolHttpConfig;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolSpeech;

import java.time.Instant;

@Component
public class ToolMapper {

    public Tool fromCreateRequest(long companyId, CreateToolRequest request) {
        return build(companyId, Instant.now(), request);
    }

    /** A definition no company owns and nothing stores here — a platform template; carries no timestamps. */
    public Tool fromTemplateRequest(CreateToolRequest request) {
        return build(null, null, request);
    }

    private Tool build(Long companyId, Instant now, CreateToolRequest request) {
        ToolHttpConfigDto http = request.http();
        ToolSpeechDto speech = request.speech();
        return new Tool(
                null,
                companyId,
                request.name(),
                request.description(),
                new ToolHttpConfig(
                        http.url(),
                        http.method(),
                        http.headers(),
                        http.body(),
                        http.queryParams(),
                        http.pathParams(),
                        http.bodySchema(),
                        http.responseTimeoutSecs(),
                        http.retryAttempts() != null ? http.retryAttempts() : 0,
                        http.auth(),
                        http.waitForResult(),
                        http.statusVariable()),
                request.dynamicVariables(),
                new ToolSpeech(
                        speech != null && speech.disableInterruptions() != null && speech.disableInterruptions(),
                        speech == null || speech.forcePreToolSpeech() == null || speech.forcePreToolSpeech(),
                        speech != null ? speech.preToolSpeech() : null),
                request.responseMapping(),
                request.maxCallsPerCall() != null ? request.maxCallsPerCall() : Tool.DEFAULT_MAX_CALLS_PER_CALL,
                request.effect(),
                request.requiresConfirmation(),
                request.readbackTemplate(),
                now,
                now
        );
    }

    /** An imported tool (UNIVERSAL-OPERATOR-PLAN B.2), as the create request it is saved through. */
    public CreateToolRequest toCreateRequest(Tool tool) {
        return new CreateToolRequest(tool.name(), tool.description(), ToolHttpConfigDto.of(tool.http()),
                tool.dynamicVariables(), ToolSpeechDto.of(tool.speech()), tool.responseMapping(),
                tool.maxCallsPerCall(), tool.effect(), tool.requiresConfirmation(), tool.readbackTemplate());
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
                ToolHttpConfigDto.of(tool.http()),
                tool.dynamicVariables(),
                ToolSpeechDto.of(tool.speech()),
                tool.responseMapping(),
                tool.maxCallsPerCall(),
                tool.effect(),
                tool.requiresConfirmation(),
                tool.readbackTemplate(),
                tool.createdAt(),
                tool.updatedAt()
        );
    }
}
