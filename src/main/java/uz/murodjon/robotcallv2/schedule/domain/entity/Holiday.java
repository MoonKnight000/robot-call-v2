package uz.murodjon.robotcallv2.schedule.domain.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import uz.murodjon.robotcallv2.schedule.domain.enums.HolidayKind;
import uz.murodjon.robotcallv2.shared.util.DateTimeProperties;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A dated exception to every business schedule of the company: a public holiday, a
 * shortened pre-holiday day, or a weekend the government moved work onto.
 *
 * @param openTime  start of the day's window; null for {@link HolidayKind#CLOSED}
 * @param closeTime end of the day's window; null for {@link HolidayKind#CLOSED}
 */
public record Holiday(
        long id,
        long companyId,
        LocalDate date,
        String name,
        HolidayKind kind,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime openTime,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime closeTime,
        Instant createdAt
) {
    public Holiday {
        if (kind == null) {
            kind = HolidayKind.CLOSED;
        }
    }

    /** What a create or update writes; id, company and creation time belong to storage. */
    public static Holiday draft(LocalDate date, String name, HolidayKind kind, LocalTime openTime, LocalTime closeTime) {
        return new Holiday(0, 0, date, name, kind, openTime, closeTime, null);
    }
}
