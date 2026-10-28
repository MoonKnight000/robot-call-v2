package uz.murodjon.robotcallv2.pack.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolAuth;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolKeyValuePair;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolParamConfig;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolEffect;

import java.util.List;
import java.util.Map;

/**
 * A tool as a pack file spells it — flat, the shape {@code packs/*.json} and every exported
 * pack were written in. It stays flat on purpose while the tool feature's own model is
 * grouped: a pack is a file a company keeps, and a change to the tool API must not make the
 * ones already exported unreadable.
 *
 * <p>The storage fields an export used to carry ({@code id}, {@code companyId},
 * {@code createdAt}, {@code updatedAt}) are accepted and dropped.
 */
@JsonIgnoreProperties({"id", "companyId", "createdAt", "updatedAt"})
public record PackTool(
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
        Map<String, String> responseMapping,
        int retryAttempts,
        int maxCallsPerCall,
        Map<String, Object> bodySchema,
        ToolEffect effect,
        Boolean requiresConfirmation,
        String readbackTemplate,
        ToolAuth auth
) {
    public PackTool {
        apiHeaders = apiHeaders == null ? List.of() : List.copyOf(apiHeaders);
        apiBody = apiBody == null ? List.of() : List.copyOf(apiBody);
        apiQueryParams = apiQueryParams == null ? List.of() : List.copyOf(apiQueryParams);
        apiPathParams = apiPathParams == null ? List.of() : List.copyOf(apiPathParams);
        dynamicVariables = dynamicVariables == null ? List.of() : List.copyOf(dynamicVariables);
        responseMapping = responseMapping == null ? Map.of() : Map.copyOf(responseMapping);
        bodySchema = bodySchema == null ? Map.of() : bodySchema;
    }
}
