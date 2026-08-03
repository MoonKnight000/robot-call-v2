package uz.murodjon.uysotvoice.inbound.dto;

import java.time.Instant;
import java.time.LocalTime;

/**
 * One row of {@code GET/POST /api/inbound-routes...} (ROADMAP C.1).
 *
 * @param didNumber          dialled number this route matches
 * @param scenarioId         scenario a matching call runs
 * @param language           BCP-47 conversation language
 * @param businessHoursStart / businessHoursEnd — null on either means no restriction (always open)
 * @param fallbackMessage    spoken (then the call is hung up) when outside business
 *                           hours or no route matches; null = hang up silently
 * @param enabled            disabled routes are never matched (soft-delete, mirrors campaign archive)
 */
public record InboundRoute(
        long id,
        String didNumber,
        long scenarioId,
        String language,
        LocalTime businessHoursStart,
        LocalTime businessHoursEnd,
        String fallbackMessage,
        boolean enabled,
        Instant createdAt
) {
}
