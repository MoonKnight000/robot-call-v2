package uz.murodjon.robotcallv2.scenario.domain.entity;

import java.time.Instant;

/**
 * One row/version of scenario (§10.7).
 */
public record Scenario(
        long id,
        String scenarioKey,
        int version,
        String name,
        String description,
        boolean builtin,
        boolean active,
        ScenarioDefinition definition,
        Instant createdAt,
        Long createdBy
) {
}
