package uz.murodjon.robotcallv2.schedule.application.port.output;

import uz.murodjon.robotcallv2.schedule.domain.entity.Holiday;
import uz.murodjon.robotcallv2.schedule.domain.entity.HolidayFilter;

import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository {

    long create(long companyId, Holiday holiday);

    void update(long companyId, long id, Holiday holiday);

    void delete(long companyId, long id);

    Holiday find(long companyId, long id);

    /** Whether another holiday already sits on that date; {@code excludeId} is the row being updated. */
    boolean existsByDate(long companyId, LocalDate date, Long excludeId);

    List<Holiday> findAll(long companyId, HolidayFilter filter);

    long count(long companyId, HolidayFilter filter);

    /** Every holiday dated {@code from}..{@code to}, both inclusive, oldest first. */
    List<Holiday> findBetween(long companyId, LocalDate from, LocalDate to);
}
