package uz.murodjon.robotcallv2.integration.application.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/platform/integration-categories body.
 *
 * @param key       UPPER_SNAKE_CASE, 2 to 32 characters, unused
 * @param sortOrder position among the categories; null — last
 */
public record CreateIntegrationCategoryRequest(
        @NotBlank String key,
        @NotBlank String name,
        Integer sortOrder
) {
}
