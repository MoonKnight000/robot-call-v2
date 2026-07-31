package uz.murodjon.uysotvoice.scenario.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param scenarioKey new stable slug for the clone; blank generates one from {@code name}
 */
public record CloneScenarioRequest(@NotBlank String name, String scenarioKey) {
}
