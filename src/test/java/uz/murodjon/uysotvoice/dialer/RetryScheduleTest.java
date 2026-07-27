package uz.murodjon.uysotvoice.dialer;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §11.2 makes the dial window a legal constraint, not a preference, so the boundary cases are
 * worth pinning: an off-by-one here is the difference between a compliant retry and a bot
 * calling a debtor at three in the morning.
 */
class RetryScheduleTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Tashkent");
    private static final LocalTime OPEN = LocalTime.of(9, 0);
    private static final LocalTime CLOSE = LocalTime.of(20, 0);
    private static final Set<DayOfWeek> WEEKDAYS = EnumSet.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    /** 2026-07-01 is a Wednesday. */
    private static ZonedDateTime at(int day, int hour, int minute) {
        return ZonedDateTime.of(LocalDate.of(2026, 7, day), LocalTime.of(hour, minute), ZONE);
    }

    @Test
    void aTimeInsideTheWindowIsLeftAlone() {
        ZonedDateTime candidate = at(1, 14, 30);
        assertThat(RetrySchedule.intoWindow(candidate, WEEKDAYS, OPEN, CLOSE)).isEqualTo(candidate);
    }

    @Test
    void tooEarlyMovesToTheOpeningTimeSameDay() {
        assertThat(RetrySchedule.intoWindow(at(1, 3, 0), WEEKDAYS, OPEN, CLOSE))
                .isEqualTo(at(1, 9, 0));
    }

    @Test
    void afterHoursMovesToTheNextMorning() {
        assertThat(RetrySchedule.intoWindow(at(1, 22, 15), WEEKDAYS, OPEN, CLOSE))
                .isEqualTo(at(2, 9, 0));
    }

    @Test
    void theClosingTimeItselfIsOutsideTheWindow() {
        // The window is [start, end): a call placed exactly at 20:00 is already late.
        assertThat(RetrySchedule.intoWindow(at(1, 20, 0), WEEKDAYS, OPEN, CLOSE))
                .isEqualTo(at(2, 9, 0));
        assertThat(RetrySchedule.intoWindow(at(1, 19, 59), WEEKDAYS, OPEN, CLOSE))
                .isEqualTo(at(1, 19, 59));
    }

    @Test
    void theOpeningTimeItselfIsInside() {
        assertThat(RetrySchedule.intoWindow(at(1, 9, 0), WEEKDAYS, OPEN, CLOSE))
                .isEqualTo(at(1, 9, 0));
    }

    @Test
    void weekendsAreSkippedToMondayMorning() {
        // 2026-07-04 is a Saturday. Nobody wants a debt-collection bot on a Sunday morning.
        assertThat(RetrySchedule.intoWindow(at(4, 11, 0), WEEKDAYS, OPEN, CLOSE))
                .isEqualTo(at(6, 9, 0));
        assertThat(RetrySchedule.intoWindow(at(5, 11, 0), WEEKDAYS, OPEN, CLOSE))
                .isEqualTo(at(6, 9, 0));
    }

    @Test
    void fridayEveningLandsOnMonday() {
        // 2026-07-03 is a Friday: after-hours, and the next two days are excluded.
        assertThat(RetrySchedule.intoWindow(at(3, 21, 0), WEEKDAYS, OPEN, CLOSE))
                .isEqualTo(at(6, 9, 0));
    }

    @Test
    void noWindowMeansNoAdjustment() {
        ZonedDateTime candidate = at(4, 3, 0);
        assertThat(RetrySchedule.intoWindow(candidate, null, null, null)).isEqualTo(candidate);
    }

    @Test
    void anEmptyDayListConstrainsOnlyTheTimeOfDay() {
        // Treating "no days" as "no constraint" is the safer reading: the alternative parks
        // every target forever with nothing in the logs to say why. The time window still
        // applies, and CampaignRow already normalizes a blank dial_days to all seven days.
        assertThat(RetrySchedule.intoWindow(at(1, 10, 0), EnumSet.noneOf(DayOfWeek.class), OPEN, CLOSE))
                .isEqualTo(at(1, 10, 0));
        assertThat(RetrySchedule.intoWindow(at(4, 3, 0), EnumSet.noneOf(DayOfWeek.class), OPEN, CLOSE))
                .isEqualTo(at(4, 9, 0));
    }

    @Test
    void nullCandidateIsHandled() {
        assertThat(RetrySchedule.intoWindow(null, WEEKDAYS, OPEN, CLOSE)).isNull();
    }

    @Test
    void delaysFallBackToTheCampaignIntervalWhenUnconfigured() {
        assertThat(RetrySchedule.delayFor(null, null, 12).toHours()).isEqualTo(12);
    }

    @Test
    void aZeroCampaignIntervalStillWaitsAnHour() {
        // A misconfigured 0 would otherwise mean an immediate redial loop on the same number.
        assertThat(RetrySchedule.delayFor(null, null, 0).toHours()).isEqualTo(1);
    }
}
