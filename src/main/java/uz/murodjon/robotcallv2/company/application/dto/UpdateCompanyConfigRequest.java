package uz.murodjon.robotcallv2.company.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.company.domain.enums.Language;
import uz.murodjon.robotcallv2.shared.util.DateTimeProperties;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * The contact-policy fields, {@code humanLikePersonaAllowed} and {@code holidays} are optional: null keeps
 * what the company already has, so a client written before they existed does not reset
 * them by omission.
 */
public record UpdateCompanyConfigRequest(
        @Schema(type = "string", pattern = DateTimeProperties.TIME_PATTERN, example = "09:00:00")
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN)
        @NotNull LocalTime dialWindowStart,
        @Schema(type = "string", pattern = DateTimeProperties.TIME_PATTERN, example = "20:00:00")
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN)
        @NotNull LocalTime dialWindowEnd,
        @NotBlank String timezone,
        @NotNull Language defaultLanguage,
        @NotEmpty List<Language> supportedLanguages,
        @Size(max = 500) String disclosureText,
        @Min(0) @Max(100) Integer maxAttemptsPerPhonePerWeek,
        @Min(0) @Max(90) Integer quietDaysAfterConversation,
        Boolean respectPromisedDate,
        Boolean humanLikePersonaAllowed,
        @Size(max = 366) List<LocalDate> holidays
) {
}
