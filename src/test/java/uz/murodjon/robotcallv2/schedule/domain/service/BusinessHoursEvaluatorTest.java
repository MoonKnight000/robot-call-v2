package uz.murodjon.robotcallv2.schedule.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.schedule.domain.entity.BusinessHoursVerdict;
import uz.murodjon.robotcallv2.schedule.domain.entity.BusinessScheduleSlot;
import uz.murodjon.robotcallv2.schedule.domain.entity.Holiday;
import uz.murodjon.robotcallv2.schedule.domain.enums.HolidayKind;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessHoursEvaluatorTest {

    private static final ZoneId TASHKENT = ZoneId.of("Asia/Tashkent");
    /** A Monday. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 14);

    private static List<BusinessScheduleSlot> weekdaysNineToSix() {
        List<BusinessScheduleSlot> slots = new ArrayList<>();
        for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            slots.add(new BusinessScheduleSlot(day, LocalTime.of(9, 0), LocalTime.of(18, 0)));
        }
        return slots;
    }

    private static Instant at(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(TASHKENT).toInstant();
    }

    @Test
    void mondayIsAMonday() {
        assertThat(MONDAY.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void openInsideASlot() {
        BusinessHoursVerdict verdict = BusinessHoursEvaluator.evaluate(weekdaysNineToSix(), List.of(), TASHKENT,
                at(MONDAY, 10, 0));

        assertThat(verdict.open()).isTrue();
        assertThat(verdict.nextOpeningAt()).isNull();
    }

    @Test
    void aMinuteBeforeOpeningIsClosedAndOpensAtNine() {
        BusinessHoursVerdict verdict = BusinessHoursEvaluator.evaluate(weekdaysNineToSix(), List.of(), TASHKENT,
                at(MONDAY, 8, 59));

        assertThat(verdict.open()).isFalse();
        assertThat(verdict.nextOpeningAt()).isEqualTo(MONDAY.atTime(9, 0).atZone(TASHKENT));
    }

    @Test
    void endTimeIsExclusive() {
        assertThat(BusinessHoursEvaluator.evaluate(weekdaysNineToSix(), List.of(), TASHKENT,
                at(MONDAY, 18, 0)).open()).isFalse();
    }

    @Test
    void fridayEveningOpensOnMonday() {
        LocalDate friday = MONDAY.plusDays(4);
        BusinessHoursVerdict verdict = BusinessHoursEvaluator.evaluate(weekdaysNineToSix(), List.of(), TASHKENT,
                at(friday, 19, 30));

        assertThat(verdict.open()).isFalse();
        assertThat(verdict.nextOpeningAt()).isEqualTo(MONDAY.plusWeeks(1).atTime(9, 0).atZone(TASHKENT));
    }

    @Test
    void lunchBreakReopensTheSameDay() {
        List<BusinessScheduleSlot> slots = List.of(
                new BusinessScheduleSlot(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(13, 0)),
                new BusinessScheduleSlot(DayOfWeek.MONDAY, LocalTime.of(14, 0), LocalTime.of(18, 0)));

        BusinessHoursVerdict verdict = BusinessHoursEvaluator.evaluate(slots, List.of(), TASHKENT, at(MONDAY, 13, 30));

        assertThat(verdict.open()).isFalse();
        assertThat(verdict.nextOpeningAt()).isEqualTo(MONDAY.atTime(14, 0).atZone(TASHKENT));
    }

    @Test
    void overnightSlotRunsPastMidnight() {
        List<BusinessScheduleSlot> slots = List.of(
                new BusinessScheduleSlot(DayOfWeek.MONDAY, LocalTime.of(22, 0), LocalTime.of(6, 0)));

        assertThat(BusinessHoursEvaluator.evaluate(slots, List.of(), TASHKENT, at(MONDAY, 23, 0)).open()).isTrue();
        assertThat(BusinessHoursEvaluator.evaluate(slots, List.of(), TASHKENT, at(MONDAY.plusDays(1), 3, 0)).open())
                .isTrue();
        assertThat(BusinessHoursEvaluator.evaluate(slots, List.of(), TASHKENT, at(MONDAY.plusDays(1), 7, 0)).open())
                .isFalse();
        assertThat(BusinessHoursEvaluator.evaluate(slots, List.of(), TASHKENT, at(MONDAY, 21, 0)).open()).isFalse();
    }

    @Test
    void closedHolidayClosesAWorkingDay() {
        Holiday navruz = new Holiday(1, 1, MONDAY, "Navro'z bayrami", HolidayKind.CLOSED, null, null, null);

        BusinessHoursVerdict verdict = BusinessHoursEvaluator.evaluate(weekdaysNineToSix(), List.of(navruz), TASHKENT,
                at(MONDAY, 10, 0));

        assertThat(verdict.open()).isFalse();
        assertThat(verdict.holidayName()).isEqualTo("Navro'z bayrami");
        assertThat(verdict.nextOpeningAt()).isEqualTo(MONDAY.plusDays(1).atTime(9, 0).atZone(TASHKENT));
    }

    @Test
    void workedSaturdayOpensWithinItsOwnWindow() {
        LocalDate saturday = MONDAY.plusDays(5);
        Holiday worked = new Holiday(1, 1, saturday, "Ko'chirilgan ish kuni", HolidayKind.WORKING_DAY,
                LocalTime.of(10, 0), LocalTime.of(15, 0), null);

        assertThat(BusinessHoursEvaluator.evaluate(weekdaysNineToSix(), List.of(worked), TASHKENT,
                at(saturday, 11, 0)).open()).isTrue();
        assertThat(BusinessHoursEvaluator.evaluate(weekdaysNineToSix(), List.of(worked), TASHKENT,
                at(saturday, 16, 0)).open()).isFalse();
    }

    @Test
    void shortDayClosesEarly() {
        Holiday eve = new Holiday(1, 1, MONDAY, "Bayram arafasi", HolidayKind.SHORT_DAY,
                LocalTime.of(9, 0), LocalTime.of(14, 0), null);

        assertThat(BusinessHoursEvaluator.evaluate(weekdaysNineToSix(), List.of(eve), TASHKENT,
                at(MONDAY, 15, 0)).open()).isFalse();
    }

    @Test
    void theZoneDecidesTheHour() {
        // 05:00 UTC is 10:00 in Tashkent: open there, even though it is "five o'clock" on the server.
        Instant fiveUtc = MONDAY.atTime(5, 0).atZone(ZoneId.of("UTC")).toInstant();

        assertThat(BusinessHoursEvaluator.evaluate(weekdaysNineToSix(), List.of(), TASHKENT, fiveUtc).open()).isTrue();
    }

    @Test
    void noDailyWindowIsAlwaysOpen() {
        assertThat(BusinessHoursEvaluator.evaluateDailyWindow(null, null, List.of(), TASHKENT, at(MONDAY, 3, 0)))
                .isEqualTo(BusinessHoursVerdict.ALWAYS_OPEN);
    }

    @Test
    void overnightDailyWindowIsOpenAtNight() {
        BusinessHoursVerdict verdict = BusinessHoursEvaluator.evaluateDailyWindow(LocalTime.of(22, 0), LocalTime.of(6, 0),
                List.of(), TASHKENT, at(MONDAY, 23, 30));

        assertThat(verdict.open()).isTrue();
    }

    @Test
    void dailyWindowAppliesOnWeekendsToo() {
        LocalDate sunday = MONDAY.minusDays(1);
        BusinessHoursVerdict verdict = BusinessHoursEvaluator.evaluateDailyWindow(LocalTime.of(9, 0), LocalTime.of(18, 0),
                List.of(), TASHKENT, at(sunday, 12, 0));

        assertThat(verdict.open()).isTrue();
        ZonedDateTime unused = verdict.nextOpeningAt();
        assertThat(unused).isNull();
    }
}
