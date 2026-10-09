package uz.murodjon.robotcallv2.aiagent.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Publishes the draft as a candidate and splits traffic to it.
 *
 * @param note           what the candidate changes, shown in the history; optional
 * @param trafficPercent 0–100 of callers; 0 — only the staging numbers
 * @param stagingPhones  test numbers that always get the candidate; optional
 */
public record StartAgentRolloutRequest(
        @Size(max = 500) String note,
        @NotNull Integer trafficPercent,
        List<String> stagingPhones
) {
}
