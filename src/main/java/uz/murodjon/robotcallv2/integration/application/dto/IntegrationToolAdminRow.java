package uz.murodjon.robotcallv2.integration.application.dto;

import uz.murodjon.robotcallv2.integration.domain.entity.IntegrationTool;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmPermission;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmScopeLevel;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmToolGroup;
import uz.murodjon.robotcallv2.integration.domain.enums.IntegrationToolKind;
import uz.murodjon.robotcallv2.tool.application.dto.CreateToolRequest;

/**
 * GET /api/platform/integration-tools row: a catalog entry with everything the platform admin edits.
 *
 * @param definition an HTTP tool's request in the shape a company tool is edited in; {@code null} for a built-in tool
 * @param modelDescription what the model reads about a built-in tool; {@code null} = the text in code.
 *                   An HTTP tool's is {@code definition.description}
 */
public record IntegrationToolAdminRow(
        long id,
        CrmProvider provider,
        String tool,
        IntegrationToolKind kind,
        CrmToolGroup group,
        String label,
        String description,
        CrmPermission permission,
        CrmScopeLevel scope,
        boolean writes,
        boolean defaultEnabled,
        boolean platformEnabled,
        int sortOrder,
        CreateToolRequest definition,
        String modelDescription
) {
    public static IntegrationToolAdminRow of(IntegrationTool tool, CreateToolRequest definition) {
        return new IntegrationToolAdminRow(tool.id(), tool.provider(), tool.key(), tool.kind(), tool.group(),
                tool.label(), tool.description(), tool.permission(), tool.scope(), tool.writes(),
                tool.defaultEnabled(), tool.platformEnabled(), tool.sortOrder(), definition,
                tool.modelDescription());
    }
}
