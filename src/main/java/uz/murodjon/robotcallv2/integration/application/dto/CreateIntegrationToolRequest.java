package uz.murodjon.robotcallv2.integration.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmPermission;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmScopeLevel;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmToolGroup;
import uz.murodjon.robotcallv2.integration.domain.enums.ToolPropagation;
import uz.murodjon.robotcallv2.tool.application.dto.CreateToolRequest;

/**
 * POST /api/platform/integration-tools body: a new HTTP tool.
 *
 * @param key        the tool's id for companies and the admin screen; UPPER_SNAKE_CASE, unique per provider
 * @param label      what the company sees; {@code definition.description} is what the model reads
 * @param permission the grant the integration's token needs for this request, or {@code null} for none
 * @param definition the request, shaped like a company tool; {@code http.url} is a path relative to the
 *                   integration host, {@code http.headers} and {@code http.auth} must be left out
 * @param applyTo    required when {@code defaultEnabled} is true: {@code ALL} gives the tool to every
 *                   company that exists, {@code NEW_ONLY} keeps it off for them and gives it to companies created later
 */
public record CreateIntegrationToolRequest(
        @NotNull CrmProvider provider,
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,63}$", message = "key must be UPPER_SNAKE_CASE, 3 to 64 characters") String key,
        @NotNull CrmToolGroup group,
        @NotBlank @Size(max = 120) String label,
        @NotBlank String description,
        CrmPermission permission,
        CrmScopeLevel scope,
        @NotNull Boolean defaultEnabled,
        @NotNull Boolean platformEnabled,
        @NotNull Integer sortOrder,
        @NotNull @Valid CreateToolRequest definition,
        ToolPropagation applyTo
) {
}
