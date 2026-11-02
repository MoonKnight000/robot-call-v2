package uz.murodjon.robotcallv2.integration.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import uz.murodjon.robotcallv2.integration.domain.enums.IntegrationAuthMethod;

import java.util.List;

/**
 * POST /api/platform/integrations body: adds a planned entry to the catalog.
 *
 * @param key         UPPER_SNAKE_CASE, 2 to 32 characters, unused
 * @param category    key of an existing category
 * @param authMethods how a company will connect it, the preferred one first; at least one
 * @param sortOrder   position among the entries; null — last
 * @param iconFileId  the logo: id of an image already uploaded through {@code POST /api/files/upload}; null — none
 */
public record CreateIntegrationRequest(
        @NotBlank String key,
        @NotBlank String name,
        @NotBlank String category,
        @NotEmpty List<IntegrationAuthMethod> authMethods,
        Integer sortOrder,
        Long iconFileId
) {
}
