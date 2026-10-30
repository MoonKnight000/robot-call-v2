package uz.murodjon.robotcallv2.integration.domain.entity;

import uz.murodjon.robotcallv2.integration.domain.enums.CrmPermission;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmScopeLevel;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmToolGroup;
import uz.murodjon.robotcallv2.integration.domain.enums.IntegrationToolKind;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolEffect;

/**
 * One tool an integration offers the voice agent, as the platform admin keeps it.
 *
 * @param key            names the handler of a built-in tool ({@code CrmTool}); unique per provider
 * @param permission     the grant the CRM token needs, or {@code null} when the tool does not call the CRM
 * @param defaultEnabled what a company that never chose gets
 * @param platformEnabled {@code false} hides the tool from every company
 * @param definition     an {@code HTTP} tool's request, with {@code http.url} a path relative to the
 *                       integration's host; {@code null} for a built-in tool
 * @param modelDescription what the model reads about a built-in tool; {@code null} keeps the text in
 *                       code. An HTTP tool's model text is {@code definition.description}
 */
public record IntegrationTool(
        long id,
        CrmProvider provider,
        String key,
        IntegrationToolKind kind,
        CrmToolGroup group,
        String label,
        String description,
        CrmPermission permission,
        CrmScopeLevel scope,
        boolean defaultEnabled,
        boolean platformEnabled,
        int sortOrder,
        Tool definition,
        String modelDescription
) {

    public static IntegrationToolBuilder builder() {
        return new IntegrationToolBuilder();
    }

    public IntegrationToolBuilder toBuilder() {
        return new IntegrationToolBuilder(this);
    }

    /** Whether the tool changes something in the CRM. */
    public boolean writes() {
        if (definition != null) {
            return definition.effect() != ToolEffect.READ;
        }
        return scope == CrmScopeLevel.SAVE || scope == CrmScopeLevel.DELETE;
    }

    /** What a company that did not choose gets, or what its own choice says. */
    public boolean isEnabledFor(Boolean override) {
        return override != null ? override : defaultEnabled;
    }
}
