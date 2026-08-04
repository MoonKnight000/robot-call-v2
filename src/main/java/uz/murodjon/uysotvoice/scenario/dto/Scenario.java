package uz.murodjon.uysotvoice.scenario.dto;

import java.time.Instant;

/**
 * One row/version of {@code scenario} (§10.7 "Ssenariylar").
 *
 * @param scenarioKey stable slug grouping every version of the same scenario (e.g.
 *                    {@code "debt-collection"}, or a generated slug for a custom one)
 * @param version     1, 2, 3... — a campaign binds to this specific row, not the key,
 *                    so editing a scenario never changes what a running campaign does
 * @param builtin     true for the 5 seed templates — read-only, cloned rather than edited
 * @param active      whether this is the version offered when attaching a *new*
 *                    campaign to {@code scenarioKey}; a campaign already bound to an
 *                    older row is unaffected by this flag
 * @param createdBy   {@code app_user} who created this scenario; {@code null} for a
 *                    built-in template or one created via the machine-to-machine
 *                    {@code X-Api-Key} (no associated person) — resolved to a name only
 *                    on {@link ScenarioRow}, matching {@code Campaign#createdBy}
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
