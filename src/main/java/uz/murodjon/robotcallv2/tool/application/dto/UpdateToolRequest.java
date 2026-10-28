package uz.murodjon.robotcallv2.tool.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolKeyValuePair;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolEffect;

import java.util.List;
import java.util.Map;

/** A partial edit: a null field, a null group or a null field inside a group keeps the tool's value. */
public record UpdateToolRequest(
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        @Size(max = 500, message = "description must be at most 500 characters")
        String description,

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
