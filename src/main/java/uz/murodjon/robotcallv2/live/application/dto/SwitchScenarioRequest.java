package uz.murodjon.robotcallv2.live.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Continue a live call under another scenario of the company; see {@code POST /api/calls/live/{channelId}/scenario}. */
public record SwitchScenarioRequest(@NotNull @Positive Long scenarioId) {
}
