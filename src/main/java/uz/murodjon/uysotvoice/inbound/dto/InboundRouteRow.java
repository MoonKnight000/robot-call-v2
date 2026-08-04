package uz.murodjon.uysotvoice.inbound.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import uz.murodjon.uysotvoice.shared.util.DateTimeProperties;

import java.time.Instant;
import java.time.LocalTime;

/**
 * {@link InboundRoute} enriched with {@code scenarioName} for the API response
 * (backend-uchun-talablar.md §3) — a projection over {@code inbound_route} and
 * {@code scenario}, so it takes the {@code <Noun>Row} suffix rather than bare
 * {@code InboundRoute}. {@link InboundRoute} itself stays the lean, name-free shape
 * used on {@code AriService}'s per-call {@code resolveByDid} hot path.
 *
 * @param scenarioName resolved from {@link InboundRoute#scenarioId()}; {@code null}
 *                     only if the scenario was since deleted (ids are otherwise
 *                     validated at write time)
 */
public record InboundRouteRow(
        long id,
        String didNumber,
        long scenarioId,
        String scenarioName,
        String language,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime businessHoursStart,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime businessHoursEnd,
        String fallbackMessage,
        boolean enabled,
        Instant createdAt
) {

    public static InboundRouteRow of(InboundRoute r, String scenarioName) {
        return new InboundRouteRow(r.id(), r.didNumber(), r.scenarioId(), scenarioName, r.language(),
                r.businessHoursStart(), r.businessHoursEnd(), r.fallbackMessage(), r.enabled(), r.createdAt());
    }
}
