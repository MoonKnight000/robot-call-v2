package uz.murodjon.robotcallv2.tool.application.dto;

import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolKeyValuePair;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolParamConfig;

import java.util.List;

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
        String preToolSpeech
) {
}
