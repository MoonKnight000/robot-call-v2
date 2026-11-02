package uz.murodjon.robotcallv2.integration.application.dto;

import uz.murodjon.robotcallv2.integration.domain.entity.IntegrationTool;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmPermission;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmScopeLevel;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmToolGroup;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolInput;
import uz.murodjon.robotcallv2.tool.domain.service.ToolOverrides;

import java.util.List;
import java.util.Map;

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
 * @param toolName   the name the model calls the tool by — what a pre-call function or a campaign target
 *                   source names it
 * @param inputs          the tool's inputs and where each comes from by default — what an agent's INTEGRATION
 *                        function may give another source
 * @param responseMapping the tool's default answer fields; empty for one that shows the whole answer
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
        boolean granted,
        String toolName,
        List<ToolInput> inputs,
        Map<String, String> responseMapping
) {
    public static CrmToolRow of(IntegrationTool tool, boolean enabled, boolean inherited, boolean granted) {
        return new CrmToolRow(tool.key(), tool.group(), tool.label(), tool.description(), tool.permission(),
                tool.scope(), tool.writes(), enabled, inherited, granted,
                tool.definition() != null ? tool.definition().name() : null,
                tool.definition() != null ? ToolOverrides.findInputs(tool.definition()) : List.of(),
                tool.definition() != null ? tool.definition().responseMapping() : Map.of());
    }
}
