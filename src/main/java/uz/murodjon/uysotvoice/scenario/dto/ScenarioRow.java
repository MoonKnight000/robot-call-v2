package uz.murodjon.uysotvoice.scenario.dto;

import java.time.Instant;

/**
 * {@link Scenario} enriched with {@code createdByName} for the API response (§10.7) — a
 * projection over {@code scenario} and {@code app_user}, so it takes the {@code
 * <Noun>Row} suffix rather than bare {@code Scenario}. {@link Scenario} itself stays the
 * lean, name-free shape used on internal hot paths (e.g. {@code AriService},
 * {@code InboundRouteService} validating a {@code scenarioId}).
 *
 * @param createdByName resolved from {@link Scenario#createdBy()}; {@code null} for a
 *                       built-in template or a scenario with no associated creator
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
        Long createdBy,
        String createdByName
) {

    public static ScenarioRow of(Scenario s, String createdByName) {
        return new ScenarioRow(s.id(), s.scenarioKey(), s.version(), s.name(), s.description(),
                s.builtin(), s.active(), s.definition(), s.createdAt(), s.createdBy(), createdByName);
    }
}
