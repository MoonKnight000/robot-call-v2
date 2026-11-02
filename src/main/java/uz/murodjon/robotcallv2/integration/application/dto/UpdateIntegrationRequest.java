package uz.murodjon.robotcallv2.integration.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import uz.murodjon.robotcallv2.integration.domain.enums.IntegrationAuthMethod;

import java.util.List;

/**
 * PUT /api/platform/integrations/{key} body: the fields of an entry the admin may change.
 *
 * @param authMethods how a company can connect it, the preferred one first; at least one
 * @param sortOrder   position among the entries; null keeps the current one
 * @param iconFileId  the logo: id of an image already uploaded through {@code POST /api/files/upload}; null removes it
 */
public record UpdateIntegrationRequest(
        @NotBlank String name,
        @NotBlank String category,
        @NotEmpty List<IntegrationAuthMethod> authMethods,
        Integer sortOrder,
        Long iconFileId
) {
}
