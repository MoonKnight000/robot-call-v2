package uz.murodjon.robotcallv2.scenario.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;

/**
 * {@code version} is the {@code ScenarioRow.version} the editor read. A scenario saved as a
 * newer version since is answered 409 instead of being replaced. Optional, so an older
 * client that does not send it keeps working.
 */
public record UpdateScenarioRequest(
        @NotBlank String name,
        String description,
        @NotNull ScenarioDefinition definition,
        Integer version
) {
}
