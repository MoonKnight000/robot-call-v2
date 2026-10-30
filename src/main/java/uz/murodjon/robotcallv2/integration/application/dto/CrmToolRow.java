package uz.murodjon.robotcallv2.integration.application.dto;

import uz.murodjon.robotcallv2.integration.domain.entity.IntegrationTool;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmPermission;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmScopeLevel;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmToolGroup;

/**
 * GET /api/settings/integrations/tools row: one CRM tool as the "Vositalar" tab shows it.
 *
 * @param tool       the tool's key — what {@code PUT} sends back
 * @param permission the grant the CRM token needs, or {@code null} when the tool does not call the CRM
 * @param writes     whether the tool changes something in the CRM
 * @param enabled    what the switch shows: the company's own choice, or the platform default
 * @param inherited  the company never chose, so {@code enabled} is the platform default and follows it
 * @param granted    whether the company's CRM grants cover the tool; when {@code false} the agent is
 *                   not offered the tool whatever {@code enabled} says, until the grant is given
 */
public record CrmToolRow(
        String tool,
        CrmToolGroup group,
        String label,
        String description,
        CrmPermission permission,
        CrmScopeLevel scope,
        boolean writes,
        boolean enabled,
        boolean inherited,
        boolean granted
) {
    public static CrmToolRow of(IntegrationTool tool, boolean enabled, boolean inherited, boolean granted) {
        return new CrmToolRow(tool.key(), tool.group(), tool.label(), tool.description(), tool.permission(),
                tool.scope(), tool.writes(), enabled, inherited, granted);
    }
}
