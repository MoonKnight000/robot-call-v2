package uz.murodjon.robotcallv2.schedule.domain.enums;

/**
 * How a dated exception changes a business schedule for one day.
 *
 * <p>{@link #SHORT_DAY} and {@link #WORKING_DAY} are evaluated the same way — the day is
 * open exactly within the holiday's own window — and differ only in what they mean to
 * whoever reads the calendar: a pre-holiday day that closes early, or a normally closed
 * day the government moved work onto.
 */
public enum HolidayKind {
    /** The whole day is closed. */
    CLOSED,
    /** A working day with its own, shorter window. */
    SHORT_DAY,
    /** A normally closed day that is worked, within its own window. */
    WORKING_DAY
}
