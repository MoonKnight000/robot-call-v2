package uz.murodjon.robotcallv2.company.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import uz.murodjon.robotcallv2.shared.util.DateTimeProperties;

import java.time.LocalTime;

/**
 * The contact-policy fields, {@code humanLikePersonaAllowed} and
 * {@code scorecardReviewThreshold} are optional: null keeps what the company already has, so
 * a client written before they existed does not reset them by omission. The telephony engine
 * is not here: only the platform sets it ({@link UpdateTelephonyEngineRequest}).
 */
public record UpdateCompanyConfigRequest(
        @Schema(type = "string", pattern = DateTimeProperties.TIME_PATTERN, example = "09:00:00")
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN)
        @NotNull LocalTime dialWindowStart,
        @Schema(type = "string", pattern = DateTimeProperties.TIME_PATTERN, example = "20:00:00")
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN)
        @NotNull LocalTime dialWindowEnd,
        @NotBlank String timezone,
        @Min(0) @Max(100) Integer maxAttemptsPerPhonePerWeek,
        @Min(0) @Max(90) Integer quietDaysAfterConversation,
        Boolean respectPromisedDate,
        Boolean humanLikePersonaAllowed,
        @Min(0) @Max(100) Integer scorecardReviewThreshold
) {
}
