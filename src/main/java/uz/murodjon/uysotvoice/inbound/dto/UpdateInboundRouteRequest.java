package uz.murodjon.uysotvoice.inbound.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record UpdateInboundRouteRequest(
        @NotBlank String didNumber,
        @NotNull Long scenarioId,
        String language,
        LocalTime businessHoursStart,
        LocalTime businessHoursEnd,
        String fallbackMessage,
        boolean enabled
) {
}
