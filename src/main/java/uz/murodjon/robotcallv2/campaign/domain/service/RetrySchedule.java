package uz.murodjon.robotcallv2.campaign.domain.service;

import java.time.LocalDate;

import uz.murodjon.robotcallv2.campaign.domain.entity.RetryRule;
import uz.murodjon.robotcallv2.campaign.domain.entity.RetryDelays;
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
     * How long to wait before retrying after {@code disposition}: the campaign's rule for
     * that disposition first, then its flat {@code retryIntervalMinutes}, then the
     * deployment's per-disposition defaults.
     */
    public static Duration delayFor(Disposition disposition, RetryDelays retry,
                                    int campaignRetryMinutes, RetryRule rule) {
        if (rule != null && rule.delayMinutes() != null && rule.delayMinutes() > 0) {
            return Duration.ofMinutes(rule.delayMinutes());
        }
        return delayFor(disposition, retry, campaignRetryMinutes);
    }

    /**
     * How long to wait before retrying after {@code disposition}.
     */
    public static Duration delayFor(Disposition disposition, RetryDelays retry,
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
        return intoWindow(candidate, allowedDays, windowStart, windowEnd, Set.of());
    }

    /** The same, stepping over the company's holidays too (NEXT-PLAN C.4). */
    public static ZonedDateTime intoWindow(ZonedDateTime candidate, Set<DayOfWeek> allowedDays,
                                           LocalTime windowStart, LocalTime windowEnd, Set<LocalDate> holidays) {
        if (candidate == null) {
            return null;
        }
        ZonedDateTime at = candidate;
        for (int day = 0; day <= MAX_DAYS_AHEAD; day++) {
            if (holidays != null && holidays.contains(at.toLocalDate())) {
                at = nextDayAtStart(at, windowStart);
                continue;
            }
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
