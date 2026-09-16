package uz.murodjon.robotcallv2.dialer.domain.service;

import uz.murodjon.robotcallv2.callrecord.domain.entity.ContactHistory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * When a number may next be called under its company's contact policy.
 *
 * <p>Per company, across campaigns: a debtor on a reminder campaign and a collections
 * campaign at once is still one person being called, and the per-campaign daily cap
 * never saw both. Spring-free and clock-free — the dialer hands in the history and the
 * time, which is what makes the arithmetic testable.
 */
public final class ContactFrequencyPolicy {

    /** The rolling window the attempt cap is counted over. */
    public static final Duration WINDOW = Duration.ofDays(7);

    private ContactFrequencyPolicy() {
    }

    /**
     * @param maxAttemptsPerWeek   cap on placed attempts in {@link #WINDOW}; 0 = none
     * @param quietDays            days after a conversation before the next call; 0 = none
     * @param respectPromisedDate  hold calls while a promise to pay is still within its date
     * @param zone                 the company's time zone — a promise "until the 15th" ends
     *                             at the company's midnight, not UTC's
     * @return the earliest moment the number may be called, or null when it may be now
     */
    public static Instant nextAllowed(int maxAttemptsPerWeek, int quietDays, boolean respectPromisedDate,
                                      ContactHistory history, Instant now, ZoneId zone) {
        if (history == null) {
            return null;
        }
        Instant earliest = null;
        List<Instant> attempts = history.recentAttempts();
        if (maxAttemptsPerWeek > 0 && attempts.size() >= maxAttemptsPerWeek) {
            // Newest first: the cap-th most recent attempt is the one whose ageing out of
            // the window frees a slot.
            Instant freed = attempts.get(maxAttemptsPerWeek - 1).plus(WINDOW);
            if (freed.isAfter(now)) {
                earliest = later(earliest, freed);
            }
        }
        if (quietDays > 0 && history.lastConversationAt() != null) {
            Instant quietUntil = history.lastConversationAt().plus(Duration.ofDays(quietDays));
            if (quietUntil.isAfter(now)) {
                earliest = later(earliest, quietUntil);
            }
        }
        if (respectPromisedDate && history.promisedUntil() != null) {
            LocalDate today = LocalDate.ofInstant(now, zone);
            if (!history.promisedUntil().isBefore(today)) {
                earliest = later(earliest, history.promisedUntil().plusDays(1).atStartOfDay(zone).toInstant());
            }
        }
        return earliest;
    }

    private static Instant later(Instant a, Instant b) {
        return a == null || b.isAfter(a) ? b : a;
    }
}
