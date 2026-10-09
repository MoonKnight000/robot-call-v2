package uz.murodjon.robotcallv2.quality.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create or full edit of a scorecard criterion ({@code POST /api/scorecard-criteria},
 * {@code PUT /api/scorecard-criteria/{id}}).
 *
 * @param enabled omitted means true
 */
public record SaveScorecardCriterionRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 1000) String instruction,
        @NotNull @Min(1) @Max(10) Integer weight,
        Boolean enabled) {

    public boolean enabledOrDefault() {
        return enabled == null || enabled;
    }
}
