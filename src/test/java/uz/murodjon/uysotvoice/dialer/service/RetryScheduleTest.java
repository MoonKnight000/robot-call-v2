package uz.murodjon.uysotvoice.dialer.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uz.murodjon.uysotvoice.dialer.config.RetryProperties;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RetryScheduleTest {

    private static final LocalTime OPEN = LocalTime.of(9, 0);
    private static final LocalTime CLOSE = LocalTime.of(18, 0);
    private static final Set<DayOfWeek> WEEKDAYS = EnumSet.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    private static ZonedDateTime at(int dayOfMonth, int hour, int minute) {
        return ZonedDateTime.parse(String.format("2026-07-%02dT%02d:%02d:00+05:00[Asia/Tashkent]", dayOfMonth, hour, minute));
    }

    @Test
    void slotInsideWindowStaysUntouched() {
        ZonedDateTime wednesdayNoon = at(1, 12, 0);
        assertThat(RetrySchedule.intoWindow(wednesdayNoon, WEEKDAYS, OPEN, CLOSE)).isEqualTo(wednesdayNoon);
    }

    @Test
    void slotBeforeWindowPullsToOpening() {
        assertThat(RetrySchedule.intoWindow(at(1, 7, 30), WEEKDAYS, OPEN, CLOSE))
                .isEqualTo(at(1, 9, 0));
    }

    @Test
    void slotAfterWindowPullsToNextDayOpening() {
        assertThat(RetrySchedule.intoWindow(at(1, 19, 0), WEEKDAYS, OPEN, CLOSE))
                .isEqualTo(at(2, 9, 0));
    }

    @Test
    void slotOnWeekendPullsToMondayOpening() {
        // 2026-07-04 is Saturday
        assertThat(RetrySchedule.intoWindow(at(4, 12, 0), WEEKDAYS, OPEN, CLOSE))
                .isEqualTo(at(6, 9, 0));
    }

    @Test
    void anEmptyDayListConstrainsOnlyTheTimeOfDay() {
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
        assertThat(RetrySchedule.delayFor(null, null, 12 * 60).toHours()).isEqualTo(12);
    }

    @Test
    void aZeroCampaignIntervalStillWaitsDefault24Hours() {
        assertThat(RetrySchedule.delayFor(null, null, 0).toHours()).isEqualTo(24);
    }
}
