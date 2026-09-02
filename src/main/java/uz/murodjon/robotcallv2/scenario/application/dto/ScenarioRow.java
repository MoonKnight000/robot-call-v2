package uz.murodjon.robotcallv2.scenario.application.dto;

import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;

import java.time.Instant;

/**
 * One row of GET/POST /api/scenarios (§10.7 "Ssenariylar").
 */
public record ScenarioRow(
        long id,
        String scenarioKey,
        int version,
        String name,
        String description,
        boolean builtin,
        boolean active,
        ScenarioDefinition definition,
        Instant createdAt,
        String createdByName
) {
    public static ScenarioRow of(Scenario s, String createdByName) {
        return new ScenarioRow(s.id(), s.scenarioKey(), s.version(), s.name(), s.description(),
                s.builtin(), s.active(), s.definition(), s.createdAt(), createdByName);
    }
}
