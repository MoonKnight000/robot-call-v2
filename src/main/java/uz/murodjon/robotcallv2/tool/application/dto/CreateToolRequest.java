package uz.murodjon.robotcallv2.tool.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolKeyValuePair;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolEffect;

import java.util.List;
import java.util.Map;

/** A new tool; {@code http.url} is required as well (checked by the service, the group is shared with updates). */
public record CreateToolRequest(
        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        @NotBlank(message = "description is required")
        @Size(max = 500, message = "description must be at most 500 characters")
        String description,

        @NotNull(message = "http is required")
        @Valid
        ToolHttpConfigDto http,

        List<ToolKeyValuePair> dynamicVariables,
        ToolSpeechDto speech,
        Map<String, String> responseMapping,
        @Min(0) Integer maxCallsPerCall,
        ToolEffect effect,
        Boolean requiresConfirmation,

        @Size(max = 500, message = "readbackTemplate must be at most 500 characters")
        String readbackTemplate
) {
}
