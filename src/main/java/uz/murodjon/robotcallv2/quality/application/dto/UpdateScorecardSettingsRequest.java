package uz.murodjon.robotcallv2.quality.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * {@code PUT /api/scorecard-settings}.
 *
 * @param reviewThreshold 0–100; 0 sends only calls with an upset client to the review inbox
 */
public record UpdateScorecardSettingsRequest(@NotNull @Min(0) @Max(100) Integer reviewThreshold) {
}
