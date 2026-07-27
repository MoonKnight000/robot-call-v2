package uz.murodjon.uysotvoice.dialer;

import uz.murodjon.uysotvoice.shared.dialog.Disposition;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Set;

/**
 * When to try a target again (PROJECT.md §5.2, §11.2).
 *
 * <p>Two decisions, both of which were previously a single 24-hour constant.
 *
 * <p><b>How long to wait</b> depends on why the last attempt failed. A subscriber who did
 * not pick up may well answer this afternoon; a call that died on a carrier error should
 * be retried in minutes; a voicemail should be retried at a different hour of the day,
 * because calling the same machine at the same time tomorrow reaches the same machine.
 * Treating all of them as "tomorrow" wastes most of a campaign's retry budget on the one
 * outcome least likely to change.
 *
 * <p><b>Whether the slot is legal</b> matters because a delay computed from "now" lands
 * wherever it lands — including 2 a.m. and Sunday. The dial window and the allowed
 * weekdays are a §11.2 obligation, and the dialer would simply hold the target until the
 * window reopened, so the retry time is moved into the window up front where it is
 * visible in {@code next_attempt_at}.
 *
 * <p>Pure functions over an explicit time: no clock of its own, so the behaviour at a
 * window boundary is testable.
 */
public final class RetrySchedule {

    /** Give up looking for an allowed weekday after this long — a misconfigured campaign. */
    private static final int MAX_DAYS_AHEAD = 14;

    private RetrySchedule() {
    }

    /**
     * How long to wait before retrying after {@code disposition}.
     *
     * @param campaignRetryHours the campaign's own {@code retry_interval_hours}, used for
     *                           outcomes with no special handling
     */
    public static Duration delayFor(Disposition disposition, DialerProperties.Retry retry,
                                    int campaignRetryHours) {
        Duration campaignDefault = Duration.ofHours(Math.max(1, campaignRetryHours));
        if (disposition == null || retry == null) {
            return campaignDefault;
        }
        return switch (disposition) {
            // Rang out or busy: the subscriber exists and may be free within hours.
            case NO_ANSWER -> Duration.ofMinutes(positive(retry.noAnswerMinutes(), 180));
            // Our fault or the carrier's — nothing about the subscriber changed, so retry
            // soon rather than burning a whole day of the retry budget.
            case FAILED -> Duration.ofMinutes(positive(retry.failedMinutes(), 15));
            // A machine answers at every hour equally, so the only thing worth varying is
            // the hour. Default is deliberately not a multiple of 24.
            case VOICEMAIL -> Duration.ofMinutes(positive(retry.voicemailMinutes(), 20 * 60));
            default -> campaignDefault;
        };
    }

    /**
     * Move {@code candidate} to the first moment at or after it that the campaign is
     * allowed to dial.
     *
     * @param allowedDays weekdays the campaign may dial on (§11.2)
     * @param windowStart start of the time-of-day window, or null for no constraint
     * @param windowEnd   end of the window (exclusive), or null for no constraint
     */
    public static ZonedDateTime intoWindow(ZonedDateTime candidate, Set<DayOfWeek> allowedDays,
                                           LocalTime windowStart, LocalTime windowEnd) {
        if (candidate == null) {
            return null;
        }
        ZonedDateTime at = candidate;
        for (int day = 0; day <= MAX_DAYS_AHEAD; day++) {
            if (allowedDays != null && !allowedDays.isEmpty()
                    && !allowedDays.contains(at.getDayOfWeek())) {
                at = nextDayAtStart(at, windowStart);
                continue;
            }
            LocalTime time = at.toLocalTime();
            if (windowStart != null && time.isBefore(windowStart)) {
                return at.with(windowStart);
            }
            if (windowEnd != null && !time.isBefore(windowEnd)) {
                at = nextDayAtStart(at, windowStart);
                continue;
            }
            return at;
        }
        // Every day of the next fortnight is excluded — the campaign's dial_days must be
        // broken. Return the raw time: the dialer's own window check still refuses to
        // dial, so this fails visibly instead of silently skipping the target forever.
        return candidate;
    }

    private static ZonedDateTime nextDayAtStart(ZonedDateTime at, LocalTime windowStart) {
        return at.plusDays(1).with(windowStart != null ? windowStart : LocalTime.MIDNIGHT);
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
