package uz.murodjon.uysotvoice.scenario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * @param scenarioKey stable slug for this scenario; blank generates one from
 *                    {@code name}. Rejected if it already exists (§10.7 "yaratish")
 */
public record CreateScenarioRequest(
        String scenarioKey,
        @NotBlank String name,
        String description,
        @NotNull ScenarioDefinition definition
) {
}
