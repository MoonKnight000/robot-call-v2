package uz.murodjon.robotcallv2.integration.application.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * PUT /api/platform/integration-categories/{key} body.
 *
 * @param sortOrder position among the categories; null keeps the current one
 */
public record UpdateIntegrationCategoryRequest(
        @NotBlank String name,
        Integer sortOrder
) {
}
