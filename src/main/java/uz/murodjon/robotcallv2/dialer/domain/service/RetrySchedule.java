package uz.murodjon.robotcallv2.dialer.domain.service;

import uz.murodjon.robotcallv2.dialer.infrastructure.config.RetryProperties;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Set;

/**
 * When to try a target again (PROJECT.md §5.2, §11.2, MASTER_ROADMAP.md §7).
 */
public final class RetrySchedule {

    /** Give up looking for an allowed weekday after this long — a misconfigured campaign. */
    private static final int MAX_DAYS_AHEAD = 14;

    /** What a retry waits when neither the campaign nor the disposition has an opinion. */
    private static final int DEFAULT_MINUTES = 24 * 60;

    private RetrySchedule() {
    }

    /**
     * How long to wait before retrying after {@code disposition}.
     */
    public static Duration delayFor(Disposition disposition, RetryProperties retry,
                                    int campaignRetryMinutes) {
        if (campaignRetryMinutes > 0) {
            return Duration.ofMinutes(campaignRetryMinutes);
        }
        if (disposition == null || retry == null) {
            return Duration.ofMinutes(DEFAULT_MINUTES);
        }
        return switch (disposition) {
            case NO_ANSWER -> Duration.ofMinutes(positive(retry.noAnswerMinutes(), 180));
            case FAILED, CARRIER_REJECTED -> Duration.ofMinutes(positive(retry.failedMinutes(), 15));
            case VOICEMAIL -> Duration.ofMinutes(positive(retry.voicemailMinutes(), 120));
            case CALLBACK_REQUESTED -> Duration.ofMinutes(60);
            default -> Duration.ofMinutes(DEFAULT_MINUTES);
        };
    }

    /**
     * Move {@code candidate} to the first moment at or after it that the campaign is
     * allowed to dial.
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
        return candidate;
    }

    private static ZonedDateTime nextDayAtStart(ZonedDateTime at, LocalTime windowStart) {
        return at.plusDays(1).with(windowStart != null ? windowStart : LocalTime.MIDNIGHT);
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
