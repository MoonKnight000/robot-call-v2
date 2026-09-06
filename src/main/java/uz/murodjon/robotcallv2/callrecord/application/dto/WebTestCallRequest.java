package uz.murodjon.robotcallv2.callrecord.application.dto;

import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;

/**
 * What to test from the browser — at most one of {@code campaignId}, {@code scenarioId},
 * {@code definition}; none means the configured default test scenario.
 *
 * @param targetId optional campaign target whose facts the call uses (needs {@code campaignId});
 *                 without it the {@code voice-agent.dialog.test-context} facts are used
 */
public record WebTestCallRequest(
        Long campaignId,
        Long targetId,
        Long scenarioId,
        ScenarioDefinition definition
) {
}
