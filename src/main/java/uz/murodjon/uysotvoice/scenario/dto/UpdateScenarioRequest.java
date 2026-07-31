package uz.murodjon.uysotvoice.scenario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Creates a new version of the scenario {@code id} points at (ROADMAP A.4
 * versioning) — never mutates the existing row, so a campaign already bound to it is
 * unaffected.
 */
public record UpdateScenarioRequest(
        @NotBlank String name,
        String description,
        @NotNull ScenarioDefinition definition
) {
}
