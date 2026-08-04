package uz.murodjon.uysotvoice.inbound.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import uz.murodjon.uysotvoice.shared.util.DateTimeProperties;

import java.time.LocalTime;

public record UpdateInboundRouteRequest(
        @NotBlank String didNumber,
        @NotNull Long scenarioId,
        String language,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime businessHoursStart,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime businessHoursEnd,
        String fallbackMessage,
        boolean enabled
) {
}
