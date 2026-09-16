package uz.murodjon.robotcallv2.callrecord.application.dto;

import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;

/**
 * What to test from the browser — at most one of {@code campaignId}, {@code scenarioId},
 * {@code definition}; none means the configured default test scenario.
 *
 * @param targetId     optional campaign target whose facts the call uses (needs {@code campaignId});
 *                     without it the {@code voice-agent.dialog.test-context} facts are used
 * @param agentVersion which version of the campaign's agent to talk to (needs {@code campaignId}):
 *                     a published number, or null for the draft — what an editor tests before publishing
 */
public record WebTestCallRequest(
        Long campaignId,
        Long targetId,
        Integer agentVersion,
        Long scenarioId,
        ScenarioDefinition definition
) {
}
