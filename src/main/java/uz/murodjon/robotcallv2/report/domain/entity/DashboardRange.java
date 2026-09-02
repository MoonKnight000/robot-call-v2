package uz.murodjon.robotcallv2.report.domain.entity;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * A resolved, validated time window for the dashboard aggregation endpoints
 * (§10.2 UI-DESIGN.md — "Bugun / 7 kun / 30 kun / Oy / Ixtiyoriy"). The panel computes
 * the actual boundaries for whichever preset the user picked and sends them as plain
 * ISO-8601 instants; this type owns the defaulting, validation, and the derived
 * previous-period / bucket-size logic every dashboard query needs.
 */
public record DashboardRange(Instant from, Instant to) {

    private static final Duration DEFAULT_SPAN = Duration.ofDays(7);
    private static final Duration MAX_SPAN = Duration.ofDays(366);

    /**
     * @param from ISO-8601 instant, or null/blank for {@code to} minus 7 days
     * @param to   ISO-8601 instant, or null/blank for now
     */
    public static DashboardRange of(String from, String to) {
        Instant end = from(to, Instant.now());
        Instant start = from(from, end.minus(DEFAULT_SPAN));
        if (!start.isBefore(end)) {
            throw new ValidationException(ErrorCode.DATE_RANGE_INVALID);
        }
        if (Duration.between(start, end).compareTo(MAX_SPAN) > 0) {
            throw new ValidationException(ErrorCode.DATE_RANGE_TOO_LONG, MAX_SPAN.toDays());
        }
        return new DashboardRange(start, end);
    }

    private static Instant from(String value, Instant fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new ValidationException(ErrorCode.INSTANT_PARSE_FAILED, value);
        }
    }

    /** The equal-length period immediately before this one, for period-over-period change. */
    public DashboardRange previous() {
        Duration span = Duration.between(from, to);
        return new DashboardRange(from.minus(span), from);
    }

    /** Bucket size for a time-series/sparkline over this range: a short range gets a fine bucket. */
    public String granularity() {
        Duration span = Duration.between(from, to);
        if (span.compareTo(Duration.ofDays(2)) <= 0) {
            return "hour";
        }
        if (span.compareTo(Duration.ofDays(62)) <= 0) {
            return "day";
        }
        return "week";
    }
}

