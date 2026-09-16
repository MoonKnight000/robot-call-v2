package uz.murodjon.robotcallv2.schedule.domain.entity;

import java.time.ZonedDateTime;

/**
 * Whether a schedule is open at one instant, and when it opens next if not.
 *
 * @param open          whether calls are within working hours right now
 * @param holidayName   the holiday that governs today, or null on an ordinary day
 * @param nextOpeningAt the next moment the schedule opens, in the schedule's zone; null
 *                      when open, or when nothing opens within the look-ahead window
 */
public record BusinessHoursVerdict(
        boolean open,
        String holidayName,
        ZonedDateTime nextOpeningAt
) {

    /** No schedule at all: always open. */
    public static final BusinessHoursVerdict ALWAYS_OPEN = new BusinessHoursVerdict(true, null, null);
}
