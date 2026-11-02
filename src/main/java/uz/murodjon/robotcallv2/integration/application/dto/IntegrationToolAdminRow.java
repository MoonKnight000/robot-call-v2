package uz.murodjon.robotcallv2.integration.application.dto;

import uz.murodjon.robotcallv2.integration.domain.entity.IntegrationTool;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmPermission;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmScopeLevel;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmToolGroup;
import uz.murodjon.robotcallv2.tool.application.dto.CreateToolRequest;

/**
 * GET /api/platform/integration-tools row: a catalog entry with everything the platform admin edits.
 *
 * @param definition the tool's request in the shape a company tool is edited in
 */
public record IntegrationToolAdminRow(
        long id,
        CrmProvider provider,
        String tool,
        CrmToolGroup group,
        String label,
        String description,
        CrmPermission permission,
        CrmScopeLevel scope,
        boolean writes,
        boolean defaultEnabled,
        boolean platformEnabled,
        int sortOrder,
        CreateToolRequest definition
) {
    public static IntegrationToolAdminRow of(IntegrationTool tool, CreateToolRequest definition) {
        return new IntegrationToolAdminRow(tool.id(), tool.provider(), tool.key(), tool.group(),
                tool.label(), tool.description(), tool.permission(), tool.scope(), tool.writes(),
                tool.defaultEnabled(), tool.platformEnabled(), tool.sortOrder(), definition);
    }
}
