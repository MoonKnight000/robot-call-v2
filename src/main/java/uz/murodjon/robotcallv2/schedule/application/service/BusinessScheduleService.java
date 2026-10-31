package uz.murodjon.robotcallv2.schedule.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.schedule.application.dto.SaveBusinessScheduleRequest;
import uz.murodjon.robotcallv2.schedule.application.dto.SaveHolidayRequest;
import uz.murodjon.robotcallv2.schedule.application.dto.ScheduleStatusResponse;
import uz.murodjon.robotcallv2.schedule.application.port.input.BusinessScheduleUseCase;
import uz.murodjon.robotcallv2.schedule.application.port.output.BusinessScheduleRepository;
import uz.murodjon.robotcallv2.schedule.application.port.output.HolidayRepository;
import uz.murodjon.robotcallv2.schedule.domain.entity.*;
import uz.murodjon.robotcallv2.schedule.domain.enums.HolidayKind;
import uz.murodjon.robotcallv2.schedule.domain.service.BusinessHoursEvaluator;
import uz.murodjon.robotcallv2.schedule.domain.service.BusinessScheduleValidator;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.time.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Business schedules and holidays (docs/INBOUND-PBX-PLAN.md §5): their CRUD, and the one
 * question every inbound call asks of them — open now, and if not, when.
 */
@Service
public class BusinessScheduleService implements BusinessScheduleUseCase {

    private static final Logger log = LoggerFactory.getLogger(BusinessScheduleService.class);

    /** Where a company with no zone configured is assumed to be. */
    private static final ZoneId FALLBACK_ZONE = ZoneId.of("Asia/Tashkent");

    /** Holidays loaded around "now": yesterday (overnight slots) through the evaluator's look-ahead. */
    private static final int HOLIDAY_WINDOW_DAYS = 16;

    /**
     * Uzbekistan's public holidays that fall on the same date every year. The two Hayit
     * days move with the lunar calendar and are announced by decree, so an admin enters
     * them — as they do the working days a decree moves.
     */
    private static final Map<MonthDay, String> FIXED_HOLIDAYS = fixedHolidays();

    private final BusinessScheduleRepository scheduleRepository;
    private final HolidayRepository holidayRepository;
    private final CompanyConfigUseCase companyConfigUseCase;
    private final AuditUseCase auditService;
    private final Clock clock;

    public BusinessScheduleService(BusinessScheduleRepository scheduleRepository, HolidayRepository holidayRepository,
                                   CompanyConfigUseCase companyConfigUseCase, AuditUseCase auditService, Clock clock) {
        this.scheduleRepository = scheduleRepository;
        this.holidayRepository = holidayRepository;
        this.companyConfigUseCase = companyConfigUseCase;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Override
    public BusinessSchedule create(long companyId, SaveBusinessScheduleRequest request) {
        BusinessScheduleValidator.validateSchedule(request.name(), request.timezone(), request.slots());
        long id = scheduleRepository.create(companyId, toSchedule(request));
        auditService.record(companyId, "BUSINESS_SCHEDULE_CREATE", "business_schedule", String.valueOf(id), request.name());
        return requireSchedule(companyId, id);
    }

    @Override
    public BusinessSchedule update(long companyId, long id, SaveBusinessScheduleRequest request) {
        requireSchedule(companyId, id);
        BusinessScheduleValidator.validateSchedule(request.name(), request.timezone(), request.slots());
        scheduleRepository.update(companyId, id, toSchedule(request));
        auditService.record(companyId, "BUSINESS_SCHEDULE_UPDATE", "business_schedule", String.valueOf(id), request.name());
        return requireSchedule(companyId, id);
    }

    @Override
    public void delete(long companyId, long id) {
        requireSchedule(companyId, id);
        scheduleRepository.delete(companyId, id);
        auditService.record(companyId, "BUSINESS_SCHEDULE_DELETE", "business_schedule", String.valueOf(id), null);
    }

    @Override
    public BusinessSchedule requireSchedule(long companyId, long id) {
        BusinessSchedule schedule = scheduleRepository.find(companyId, id);
        if (schedule == null) {
            throw new NotFoundException(ErrorCode.BUSINESS_SCHEDULE_NOT_FOUND, id);
        }
        return schedule;
    }

    @Override
    public PageableData<BusinessSchedule> list(long companyId, BusinessScheduleFilter filter) {
        return PageableData.of(scheduleRepository.findAll(companyId, filter), filter.pageOrDefault(),
                filter.sizeOrDefault(), scheduleRepository.count(companyId));
    }

    @Override
    public ScheduleStatusResponse findStatus(long companyId, long id) {
        BusinessSchedule schedule = requireSchedule(companyId, id);
        ZoneId zone = zoneOf(companyId, schedule.timezone());
        BusinessHoursVerdict verdict = evaluate(companyId, id, clock.instant());
        return new ScheduleStatusResponse(verdict.open(), verdict.holidayName(),
                verdict.nextOpeningAt() != null ? verdict.nextOpeningAt().toOffsetDateTime() : null, zone.getId());
    }

    @Override
    public Holiday createHoliday(long companyId, SaveHolidayRequest request) {
        BusinessScheduleValidator.validateHoliday(request.date(), request.name(), request.kind(),
                request.openTime(), request.closeTime());
        if (holidayRepository.existsByDate(companyId, request.date(), null)) {
            throw new ConflictException(ErrorCode.HOLIDAY_DATE_EXISTS, request.date());
        }
        long id = holidayRepository.create(companyId, toHoliday(request));
        auditService.record(companyId, "HOLIDAY_CREATE", "holiday", String.valueOf(id), request.date().toString());
        return requireHoliday(companyId, id);
    }

    @Override
    public Holiday updateHoliday(long companyId, long id, SaveHolidayRequest request) {
        requireHoliday(companyId, id);
        BusinessScheduleValidator.validateHoliday(request.date(), request.name(), request.kind(),
                request.openTime(), request.closeTime());
        if (holidayRepository.existsByDate(companyId, request.date(), id)) {
            throw new ConflictException(ErrorCode.HOLIDAY_DATE_EXISTS, request.date());
        }
        holidayRepository.update(companyId, id, toHoliday(request));
        auditService.record(companyId, "HOLIDAY_UPDATE", "holiday", String.valueOf(id), request.date().toString());
        return requireHoliday(companyId, id);
    }

    @Override
    public void deleteHoliday(long companyId, long id) {
        requireHoliday(companyId, id);
        holidayRepository.delete(companyId, id);
        auditService.record(companyId, "HOLIDAY_DELETE", "holiday", String.valueOf(id), null);
    }

    @Override
    public PageableData<Holiday> listHolidays(long companyId, HolidayFilter filter) {
        return PageableData.of(holidayRepository.findAll(companyId, filter), filter.pageOrDefault(),
                filter.sizeOrDefault(), holidayRepository.count(companyId, filter));
    }

    @Override
    public List<Holiday> createFixedHolidays(long companyId, int year) {
        if (year < 2000 || year > 2100) {
            throw new ValidationException(ErrorCode.HOLIDAY_YEAR_OUT_OF_RANGE, 2000, 2100);
        }
        List<Holiday> created = new ArrayList<>();
        for (Map.Entry<MonthDay, String> fixed : FIXED_HOLIDAYS.entrySet()) {
            LocalDate date = fixed.getKey().atYear(year);
            if (holidayRepository.existsByDate(companyId, date, null)) {
                continue;
            }
            long id = holidayRepository.create(companyId,
                    Holiday.draft(date, fixed.getValue(), HolidayKind.CLOSED, null, null));
            created.add(requireHoliday(companyId, id));
        }
        auditService.record(companyId, "HOLIDAY_FIXED_CREATE", "holiday", String.valueOf(year),
                created.size() + " added");
        return created;
    }

    @Override
    public Set<LocalDate> findHolidayDates(long companyId, LocalDate from, LocalDate to) {
        return holidayRepository.findBetween(companyId, from, to).stream()
                .filter(h -> h.kind() == HolidayKind.CLOSED)
                .map(Holiday::date)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean isHoliday(long companyId, LocalDate date) {
        return holidayRepository.findBetween(companyId, date, date).stream()
                .anyMatch(h -> h.kind() == HolidayKind.CLOSED);
    }

    @Override
    public BusinessHoursVerdict evaluate(long companyId, long scheduleId, Instant now) {
        BusinessSchedule schedule = scheduleRepository.find(companyId, scheduleId);
        if (schedule == null) {
            log.warn("Schedule {} of company {} no longer exists — treating it as always open", scheduleId, companyId);
            return BusinessHoursVerdict.ALWAYS_OPEN;
        }
        ZoneId zone = zoneOf(companyId, schedule.timezone());
        return BusinessHoursEvaluator.evaluate(schedule.slots(), holidaysAround(companyId, zone, now), zone, now);
    }

    @Override
    public BusinessHoursVerdict evaluateDailyWindow(long companyId, LocalTime start, LocalTime end, Instant now) {
        ZoneId zone = zoneOf(companyId, null);
        return BusinessHoursEvaluator.evaluateDailyWindow(start, end, holidaysAround(companyId, zone, now), zone, now);
    }

    @Override
    public ZoneId findZone(long companyId, Long scheduleId) {
        BusinessSchedule schedule = scheduleId != null ? scheduleRepository.find(companyId, scheduleId) : null;
        return zoneOf(companyId, schedule != null ? schedule.timezone() : null);
    }

    private Holiday requireHoliday(long companyId, long id) {
        Holiday holiday = holidayRepository.find(companyId, id);
        if (holiday == null) {
            throw new NotFoundException(ErrorCode.HOLIDAY_NOT_FOUND, id);
        }
        return holiday;
    }

    private List<Holiday> holidaysAround(long companyId, ZoneId zone, Instant now) {
        LocalDate today = now.atZone(zone).toLocalDate();
        return holidayRepository.findBetween(companyId, today.minusDays(1), today.plusDays(HOLIDAY_WINDOW_DAYS));
    }

    private ZoneId zoneOf(long companyId, String timezone) {
        String zone = timezone;
        if (zone == null || zone.isBlank()) {
            CompanyConfig config = companyConfigUseCase.find(companyId);
            zone = config != null ? config.timezone() : null;
        }
        if (zone == null || zone.isBlank()) {
            return FALLBACK_ZONE;
        }
        try {
            return ZoneId.of(zone.trim());
        } catch (DateTimeException e) {
            log.warn("Company {} has an unusable time zone '{}' — reading its hours in {}", companyId, zone, FALLBACK_ZONE);
            return FALLBACK_ZONE;
        }
    }

    private static BusinessSchedule toSchedule(SaveBusinessScheduleRequest request) {
        String timezone = request.timezone() == null || request.timezone().isBlank() ? null : request.timezone().trim();
        return BusinessSchedule.draft(request.name().trim(), timezone, request.slots());
    }

    private static Holiday toHoliday(SaveHolidayRequest request) {
        HolidayKind kind = request.kind() != null ? request.kind() : HolidayKind.CLOSED;
        boolean closed = kind == HolidayKind.CLOSED;
        return Holiday.draft(request.date(), request.name().trim(), kind,
                closed ? null : request.openTime(), closed ? null : request.closeTime());
    }

    private static Map<MonthDay, String> fixedHolidays() {
        Map<MonthDay, String> holidays = new LinkedHashMap<>();
        holidays.put(MonthDay.of(1, 1), "Yangi yil");
        holidays.put(MonthDay.of(3, 8), "Xalqaro xotin-qizlar kuni");
        holidays.put(MonthDay.of(3, 21), "Navro'z bayrami");
        holidays.put(MonthDay.of(5, 9), "Xotira va qadrlash kuni");
        holidays.put(MonthDay.of(9, 1), "Mustaqillik kuni");
        holidays.put(MonthDay.of(10, 1), "O'qituvchi va murabbiylar kuni");
        holidays.put(MonthDay.of(12, 8), "Konstitutsiya kuni");
        return holidays;
    }
}
