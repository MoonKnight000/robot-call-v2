package uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter;

import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

/** What the report adapters share: parameter conversions and null-aware column readers. */
final class ReportSql {

    private ReportSql() {
    }

    /** The names of those dispositions, to bind to an {@code IN (:param)} list. */
    static List<String> names(Disposition... dispositions) {
        return Arrays.stream(dispositions).map(Enum::name).toList();
    }

    /** Bound as an offset date-time, which the PostgreSQL driver maps to timestamptz — unlike a bare Instant. */
    static OffsetDateTime toTimestamptz(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    static double rate(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    static <T> T firstOrNull(List<T> values) {
        return values.isEmpty() ? null : values.get(0);
    }

    static Long longOrNull(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    static Integer intOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    static Double doubleOrNull(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    static Instant instantOrNull(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    static LocalDate localDateOrNull(ResultSet rs, String column) throws SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    /** The constant, or null for a missing value or one this build no longer knows. */
    static <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
