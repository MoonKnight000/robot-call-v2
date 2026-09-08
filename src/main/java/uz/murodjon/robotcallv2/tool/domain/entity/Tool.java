package uz.murodjon.robotcallv2.tool.domain.entity;

import java.time.Instant;
import java.util.List;

public record Tool(
        Long id,
        Long companyId,
        String name,
        String description,
        String apiUrl,
        String apiMethod,
        List<ToolKeyValuePair> apiHeaders,
        List<ToolParamConfig> apiBody,
        List<ToolParamConfig> apiQueryParams,
        List<ToolParamConfig> apiPathParams,
        Integer responseTimeoutSecs,
        List<ToolKeyValuePair> dynamicVariables,
        boolean disableInterruptions,
        boolean forcePreToolSpeech,
        String preToolSpeech,
        Instant createdAt,
        Instant updatedAt
) {
    public Tool {
        apiMethod = (apiMethod == null || apiMethod.isBlank()) ? "POST" : apiMethod.toUpperCase();
        apiHeaders = apiHeaders == null ? List.of() : List.copyOf(apiHeaders);
        apiBody = apiBody == null ? List.of() : List.copyOf(apiBody);
        apiQueryParams = apiQueryParams == null ? List.of() : List.copyOf(apiQueryParams);
        apiPathParams = apiPathParams == null ? List.of() : List.copyOf(apiPathParams);
        dynamicVariables = dynamicVariables == null ? List.of() : List.copyOf(dynamicVariables);
        responseTimeoutSecs = responseTimeoutSecs == null ? 10 : responseTimeoutSecs;
    }
}
