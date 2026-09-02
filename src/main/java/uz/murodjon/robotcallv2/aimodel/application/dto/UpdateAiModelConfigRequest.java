package uz.murodjon.robotcallv2.aimodel.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Every field optional — null clears the override back to the process default.
 */
public record UpdateAiModelConfigRequest(
        String model,
        @Min(0) @Max(2) Double temperature,
        @Min(1) Integer maxOutputTokens,
        @Min(1) Integer maxCallSeconds,
        @Min(1) Long maxTokensPerCall
) {
}
