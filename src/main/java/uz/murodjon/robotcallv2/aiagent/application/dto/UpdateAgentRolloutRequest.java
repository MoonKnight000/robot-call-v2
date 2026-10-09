package uz.murodjon.robotcallv2.aiagent.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** A new split for the running rollout (e.g. 10% → 50%); the candidate stays. */
public record UpdateAgentRolloutRequest(
        @NotNull Integer trafficPercent,
        List<String> stagingPhones
) {
}
