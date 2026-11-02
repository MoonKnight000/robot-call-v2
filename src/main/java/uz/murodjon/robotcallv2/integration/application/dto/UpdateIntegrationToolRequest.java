package uz.murodjon.robotcallv2.integration.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmPermission;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmScopeLevel;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmToolGroup;
import uz.murodjon.robotcallv2.integration.domain.enums.ToolPropagation;
import uz.murodjon.robotcallv2.tool.application.dto.CreateToolRequest;

/**
 * PUT /api/platform/integration-tools/{id} body.
 *
 * @param permission the grant the tool's request needs ({@code null} for none)
 * @param scope      see {@code permission}
 * @param definition the tool's new request, replacing the old one; required
 * @param applyTo    whom a changed {@code defaultEnabled} reaches; required exactly when
 *                   {@code defaultEnabled} differs from the stored value, ignored otherwise
 */
public record UpdateIntegrationToolRequest(
        @NotBlank @Size(max = 120) String label,
        @NotBlank String description,
        @NotNull CrmToolGroup group,
        @NotNull Boolean defaultEnabled,
        @NotNull Boolean platformEnabled,
        @NotNull Integer sortOrder,
        CrmPermission permission,
        CrmScopeLevel scope,
        @Valid CreateToolRequest definition,
        ToolPropagation applyTo
) {
}
