package uz.murodjon.uysotvoice.inbound.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import uz.murodjon.uysotvoice.shared.util.DateTimeProperties;

import java.time.LocalTime;

/**
 * @param didNumber          dialled number this route matches (E.164-ish, 3-15 digits)
 * @param scenarioId         id of a scenario from {@code GET/POST /api/scenarios/list}
 * @param language           BCP-47; blank defaults to {@code uz-UZ}
 * @param businessHoursStart / businessHoursEnd — omit either for "always open"
 * @param fallbackMessage    spoken then hangup outside business hours; omit to hang up silently
 */
public record CreateInboundRouteRequest(
        @NotBlank String didNumber,
        @NotNull Long scenarioId,
        String language,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime businessHoursStart,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime businessHoursEnd,
        String fallbackMessage
) {
}
