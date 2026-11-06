package uz.murodjon.robotcallv2.quality.application.dto;

/**
 * The company's scorecard settings ({@code GET/PUT /api/scorecard-settings}).
 *
 * @param reviewThreshold an operator call scored below this (0–100) goes to the review inbox
 */
public record ScorecardSettingsResponse(int reviewThreshold) {
}
