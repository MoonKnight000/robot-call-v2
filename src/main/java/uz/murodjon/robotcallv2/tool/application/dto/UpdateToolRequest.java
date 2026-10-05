package uz.murodjon.robotcallv2.tool.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolAuth;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolKeyValuePair;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolParamConfig;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolEffect;

import java.util.List;
import java.util.Map;

public record UpdateToolRequest(
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        @Size(max = 500, message = "description must be at most 500 characters")
        String description,

        @Size(max = 1000, message = "apiUrl must be at most 1000 characters")
        String apiUrl,

        String apiMethod,
        List<ToolKeyValuePair> apiHeaders,
        List<ToolParamConfig> apiBody,
        List<ToolParamConfig> apiQueryParams,
        List<ToolParamConfig> apiPathParams,
        Integer responseTimeoutSecs,
        List<ToolKeyValuePair> dynamicVariables,
        Boolean disableInterruptions,
        Boolean forcePreToolSpeech,
        String preToolSpeech,
        Map<String, String> responseMapping,
        @Min(0) @Max(Tool.MAX_RETRY_ATTEMPTS) Integer retryAttempts,
        @Min(0) Integer maxCallsPerCall,
        Map<String, Object> bodySchema,
        ToolEffect effect,
        Boolean requiresConfirmation,

        @Size(max = 500, message = "readbackTemplate must be at most 500 characters")
        String readbackTemplate,

        /** Null keeps the tool's auth; one without a {@code type} ({@code {}}) removes it. */
        ToolAuth auth
) {
}
