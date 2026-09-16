package uz.murodjon.robotcallv2.schedule.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.schedule.domain.enums.HolidayKind;
import uz.murodjon.robotcallv2.shared.util.DateTimeProperties;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Body of POST/PUT /api/holidays.
 *
 * @param kind      null is {@link HolidayKind#CLOSED}
 * @param openTime  required unless the day is CLOSED
 * @param closeTime required unless the day is CLOSED
 */
public record SaveHolidayRequest(
        @NotNull LocalDate date,
        @NotBlank String name,
        HolidayKind kind,
        @Schema(type = "string", pattern = DateTimeProperties.TIME_PATTERN, example = "09:00:00")
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN)
        LocalTime openTime,
        @Schema(type = "string", pattern = DateTimeProperties.TIME_PATTERN, example = "14:00:00")
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN)
        LocalTime closeTime
) {
}
