package uz.murodjon.robotcallv2.widget.domain.entity;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One widget call counted against its agent's limits: a line held until the session is
 * used or abandoned, and one call of the agent's day.
 *
 * @param agentId          the agent the call is for
 * @param reservationId    names this booking among the agent's live sessions, so it can be handed back
 * @param bookedAt         when it was booked; the line is free again {@link #SESSION_TTL} later
 * @param day              the agent's calendar day (Tashkent) the call counts against
 * @param concurrentLimit  live sessions the agent allows at once
 * @param dailyLimit       widget calls the agent allows per day
 */
public record WidgetCallBooking(
        long agentId,
        String reservationId,
        Instant bookedAt,
        LocalDate day,
        int concurrentLimit,
        int dailyLimit
) {

    /**
     * How long a booked session counts against the concurrency limit before it is assumed
     * abandoned. Matches the five minutes Asterisk keeps the pending session for: past
     * that the browser can no longer dial in, so the slot is genuinely free.
     */
    public static final Duration SESSION_TTL = Duration.ofMinutes(5);

    /** Sessions booked before this instant no longer hold a line. */
    public Instant computeAbandonedBefore() {
        return bookedAt.minus(SESSION_TTL);
    }
}
