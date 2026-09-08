package uz.murodjon.robotcallv2.tool.application.dto;

import uz.murodjon.robotcallv2.tool.domain.entity.ToolKeyValuePair;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolParamConfig;

import java.time.Instant;
import java.util.List;

public record ToolRow(
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
}
