package uz.murodjon.robotcallv2.schedule.domain.service;

import uz.murodjon.robotcallv2.schedule.domain.entity.BusinessHoursVerdict;
import uz.murodjon.robotcallv2.schedule.domain.entity.BusinessScheduleSlot;
import uz.murodjon.robotcallv2.schedule.domain.entity.Holiday;
import uz.murodjon.robotcallv2.schedule.domain.enums.HolidayKind;

import java.time.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Whether a weekly schedule, with its holidays, is open at an instant — and when it opens
 * next. Pure: the clock and the zone are arguments, so every rule here is a unit test.
 *
 * <p>The rules, in order:
 * <ol>
 *   <li>A holiday on the date replaces that day's slots. {@link HolidayKind#CLOSED} is
 *       closed all day; any other kind is open exactly within its own window.</li>
 *   <li>Otherwise the date's weekday slots apply; a slot whose end is before its start
 *       runs past midnight, so 22:00–06:00 on Monday is also open until 06:00 on
 *       Tuesday — unless Tuesday is a holiday, which then decides alone.</li>
 * </ol>
 */
public final class BusinessHoursEvaluator {

    /** How far ahead the next opening is looked for; two weeks covers any real holiday run. */
    static final int LOOKAHEAD_DAYS = 14;

    private BusinessHoursEvaluator() {
    }

    public static BusinessHoursVerdict evaluate(List<BusinessScheduleSlot> slots, List<Holiday> holidays,
                                                ZoneId zone, Instant now) {
        Map<LocalDate, Holiday> byDate = indexByDate(holidays);
        ZonedDateTime local = now.atZone(zone);
        LocalDate today = local.toLocalDate();
        Holiday todays = byDate.get(today);
        boolean open = isOpen(slots, byDate, today, local.toLocalTime());
        return new BusinessHoursVerdict(open, todays != null ? todays.name() : null,
                open ? null : nextOpening(slots, byDate, local));
    }

    /**
     * The same, for a route that only has a daily window ({@code business_hours_start/end}):
     * that window on every day of the week. No window at all means always open — a route
     * nobody gave hours to is one meant to answer around the clock, holidays included.
     */
    public static BusinessHoursVerdict evaluateDailyWindow(LocalTime start, LocalTime end, List<Holiday> holidays,
                                                           ZoneId zone, Instant now) {
        if (start == null || end == null || start.equals(end)) {
            return BusinessHoursVerdict.ALWAYS_OPEN;
        }
        List<BusinessScheduleSlot> everyDay = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            everyDay.add(new BusinessScheduleSlot(day, start, end));
        }
        return evaluate(everyDay, holidays, zone, now);
    }

    private static boolean isOpen(List<BusinessScheduleSlot> slots, Map<LocalDate, Holiday> byDate,
                                  LocalDate date, LocalTime time) {
        Holiday holiday = byDate.get(date);
        if (holiday != null) {
            return holiday.kind() != HolidayKind.CLOSED && within(holiday.openTime(), holiday.closeTime(), time);
        }
        for (BusinessScheduleSlot slot : slots) {
            if (slot.dayOfWeek() != date.getDayOfWeek()) {
                continue;
            }
            boolean inside = slot.overnight()
                    ? !time.isBefore(slot.startTime())
                    : within(slot.startTime(), slot.endTime(), time);
            if (inside) {
                return true;
            }
        }
        // Yesterday's overnight slot still running this morning.
        LocalDate previous = date.minusDays(1);
        if (!byDate.containsKey(previous)) {
            for (BusinessScheduleSlot slot : slots) {
                if (slot.dayOfWeek() == previous.getDayOfWeek() && slot.overnight()
                        && time.isBefore(slot.endTime())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ZonedDateTime nextOpening(List<BusinessScheduleSlot> slots, Map<LocalDate, Holiday> byDate,
                                             ZonedDateTime from) {
        for (int offset = 0; offset <= LOOKAHEAD_DAYS; offset++) {
            LocalDate date = from.toLocalDate().plusDays(offset);
            List<LocalTime> starts = new ArrayList<>();
            Holiday holiday = byDate.get(date);
            if (holiday != null) {
                if (holiday.kind() != HolidayKind.CLOSED && holiday.openTime() != null) {
                    starts.add(holiday.openTime());
                }
            } else {
                for (BusinessScheduleSlot slot : slots) {
                    if (slot.dayOfWeek() == date.getDayOfWeek()) {
                        starts.add(slot.startTime());
                    }
                }
            }
            starts.sort(null);
            for (LocalTime start : starts) {
                ZonedDateTime at = date.atTime(start).atZone(from.getZone());
                if (at.isAfter(from)) {
                    return at;
                }
            }
        }
        return null;
    }

    private static boolean within(LocalTime start, LocalTime end, LocalTime time) {
        return start != null && end != null && !time.isBefore(start) && time.isBefore(end);
    }

    private static Map<LocalDate, Holiday> indexByDate(List<Holiday> holidays) {
        Map<LocalDate, Holiday> byDate = new HashMap<>();
        if (holidays != null) {
            for (Holiday holiday : holidays) {
                byDate.put(holiday.date(), holiday);
            }
        }
        return byDate;
    }
}
