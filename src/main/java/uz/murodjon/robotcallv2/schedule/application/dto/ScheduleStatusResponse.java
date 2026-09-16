package uz.murodjon.robotcallv2.schedule.application.dto;

import java.time.OffsetDateTime;

/**
 * GET /api/schedules/{id}/status — what a caller would get if they rang now.
 *
 * @param open          within working hours right now
 * @param holidayName   the holiday that governs today, or null
 * @param nextOpeningAt when it opens next (in the schedule's zone); null when open
 * @param timezone      the zone the schedule is read in
 */
public record ScheduleStatusResponse(
        boolean open,
        String holidayName,
        OffsetDateTime nextOpeningAt,
        String timezone
) {
}
