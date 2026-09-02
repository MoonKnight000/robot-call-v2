package uz.murodjon.robotcallv2.scenario.application.dto;

import jakarta.validation.constraints.NotBlank;

public record CloneScenarioRequest(
        String scenarioKey,
        @NotBlank String name
) {
}
