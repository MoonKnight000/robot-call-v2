package uz.murodjon.robotcallv2.scenario.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;

public record CreateScenarioRequest(
        String scenarioKey,
        @NotBlank String name,
        String description,
        @NotNull ScenarioDefinition definition
) {
}
