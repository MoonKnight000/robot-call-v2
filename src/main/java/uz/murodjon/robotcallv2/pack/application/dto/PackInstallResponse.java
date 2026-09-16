package uz.murodjon.robotcallv2.pack.application.dto;

import uz.murodjon.robotcallv2.pack.domain.entity.PackSecret;

import java.util.List;

/**
 * What an install created, and what is still left to the company before the agent can take
 * a call.
 *
 * @param catalogSourceId   the catalog feed being indexed; null when no {@code catalogUrl} was given
 * @param conversionGoalKey the goal created now; null when the pack has none or the company
 *                          already had a goal under its key — that one is left as it was
 * @param missingSecrets    the pack's secrets this company has not set yet — every tool call
 *                          fails until they are
 */
public record PackInstallResponse(
        long agentId,
        List<Long> toolIds,
        Long catalogSourceId,
        List<Long> testCaseIds,
        String conversionGoalKey,
        List<PackSecret> missingSecrets
) {
}
