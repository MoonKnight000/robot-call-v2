package uz.murodjon.robotcallv2.schedule.application.port.input;

import uz.murodjon.robotcallv2.schedule.application.dto.SaveBusinessScheduleRequest;
import uz.murodjon.robotcallv2.schedule.application.dto.SaveHolidayRequest;
import uz.murodjon.robotcallv2.schedule.application.dto.ScheduleStatusResponse;
import uz.murodjon.robotcallv2.schedule.domain.entity.*;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

public interface BusinessScheduleUseCase {

    BusinessSchedule create(long companyId, SaveBusinessScheduleRequest request);

    BusinessSchedule update(long companyId, long id, SaveBusinessScheduleRequest request);

    void delete(long companyId, long id);

    BusinessSchedule requireSchedule(long companyId, long id);

    PageableData<BusinessSchedule> list(long companyId, BusinessScheduleFilter filter);

    ScheduleStatusResponse findStatus(long companyId, long id);

    Holiday createHoliday(long companyId, SaveHolidayRequest request);

    Holiday updateHoliday(long companyId, long id, SaveHolidayRequest request);

    void deleteHoliday(long companyId, long id);

    PageableData<Holiday> listHolidays(long companyId, HolidayFilter filter);

    /** Adds the year's fixed-date public holidays the company does not have yet; returns the new rows. */
    List<Holiday> createFixedHolidays(long companyId, int year);

    /** Whether the schedule is open at {@code now}; a schedule that no longer exists is always open. */
    BusinessHoursVerdict evaluate(long companyId, long scheduleId, Instant now);

    /** The same for a route's own daily window (legacy {@code business_hours_start/end}), in the company's zone. */
    BusinessHoursVerdict evaluateDailyWindow(long companyId, LocalTime start, LocalTime end, Instant now);

    /** The zone a schedule's times are read in: its own, else the company's. */
    ZoneId findZone(long companyId, Long scheduleId);
}
