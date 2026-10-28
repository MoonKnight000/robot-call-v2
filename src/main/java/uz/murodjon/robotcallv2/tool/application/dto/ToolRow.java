package uz.murodjon.robotcallv2.tool.application.dto;

import uz.murodjon.robotcallv2.tool.domain.entity.ToolKeyValuePair;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolEffect;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** A tool as the API answers it; the groups are the ones {@link UpdateToolRequest} takes. */
public record ToolRow(
        Long id,
        Long companyId,
        String name,
        String description,
        ToolHttpConfigDto http,
        List<ToolKeyValuePair> dynamicVariables,
        ToolSpeechDto speech,
        Map<String, String> responseMapping,
        int maxCallsPerCall,
        ToolEffect effect,
        boolean requiresConfirmation,
        String readbackTemplate,
        Instant createdAt,
        Instant updatedAt
) {
}
