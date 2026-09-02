package uz.murodjon.robotcallv2.company.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.company.domain.enums.Language;
import uz.murodjon.robotcallv2.shared.util.DateTimeProperties;

import java.time.LocalTime;
import java.util.List;

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
        @Size(max = 500) String disclosureText
) {
}
