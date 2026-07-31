package uz.murodjon.uysotvoice.scenario.dto;

import java.util.List;

/** Result of {@code POST /api/scenarios/validate} (ROADMAP A.4). */
public record ScenarioValidationResult(boolean valid, List<String> errors) {
}
