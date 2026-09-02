package uz.murodjon.robotcallv2.report.domain.enums;

import java.time.Duration;

/**
 * How often a {@code report_schedule} row sends its email (§10.10 "Jadval bo'yicha
 * yuborish"). {@link #span()} doubles as both "how long to wait before the next send"
 * and "what window the attached report covers" — a weekly email reports on the week
 * that just ended.
 */
public enum ReportPeriodicity {
    DAILY(Duration.ofDays(1)),
    WEEKLY(Duration.ofDays(7)),
    MONTHLY(Duration.ofDays(30));

    private final Duration span;

    ReportPeriodicity(Duration span) {
        this.span = span;
    }

    public Duration span() {
        return span;
    }
}

