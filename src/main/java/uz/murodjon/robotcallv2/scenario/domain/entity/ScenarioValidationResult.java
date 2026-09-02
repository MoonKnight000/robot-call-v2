package uz.murodjon.robotcallv2.scenario.domain.entity;

import java.util.List;

public record ScenarioValidationResult(
        boolean valid,
        List<String> errors
) {
}
