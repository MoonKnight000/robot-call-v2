package uz.murodjon.robotcallv2.schedule.domain.service;

import uz.murodjon.robotcallv2.schedule.domain.entity.BusinessScheduleSlot;
import uz.murodjon.robotcallv2.schedule.domain.enums.HolidayKind;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/** Shape rules for schedules and holidays, checked before anything is stored. */
public final class BusinessScheduleValidator {

    private BusinessScheduleValidator() {
    }

    public static void validateSchedule(String name, String timezone, List<BusinessScheduleSlot> slots) {
        if (name == null || name.isBlank()) {
            throw new ValidationException(ErrorCode.BUSINESS_SCHEDULE_NAME_REQUIRED);
        }
        validateZone(timezone);
        if (slots == null || slots.isEmpty()) {
            throw new ValidationException(ErrorCode.BUSINESS_SCHEDULE_SLOTS_REQUIRED);
        }
        for (BusinessScheduleSlot slot : slots) {
            if (slot == null || slot.dayOfWeek() == null || slot.startTime() == null || slot.endTime() == null) {
                throw new ValidationException(ErrorCode.BUSINESS_SCHEDULE_SLOT_INCOMPLETE);
            }
            if (slot.startTime().equals(slot.endTime())) {
                throw new ValidationException(ErrorCode.BUSINESS_SCHEDULE_SLOT_EMPTY, slot.dayOfWeek());
            }
        }
    }

    public static void validateHoliday(LocalDate date, String name, HolidayKind kind,
                                       LocalTime openTime, LocalTime closeTime) {
        if (date == null) {
            throw new ValidationException(ErrorCode.HOLIDAY_DATE_REQUIRED);
        }
        if (name == null || name.isBlank()) {
            throw new ValidationException(ErrorCode.HOLIDAY_NAME_REQUIRED);
        }
        if (kind != null && kind != HolidayKind.CLOSED) {
            if (openTime == null || closeTime == null || !openTime.isBefore(closeTime)) {
                throw new ValidationException(ErrorCode.HOLIDAY_HOURS_INVALID, kind);
            }
        }
    }

    private static void validateZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return;
        }
        try {
            ZoneId.of(timezone.trim());
        } catch (DateTimeException e) {
            throw new ValidationException(ErrorCode.BUSINESS_SCHEDULE_TIMEZONE_UNKNOWN, timezone);
        }
    }
}
