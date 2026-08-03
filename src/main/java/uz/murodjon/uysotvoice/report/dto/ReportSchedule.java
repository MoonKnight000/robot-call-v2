package uz.murodjon.uysotvoice.report.dto;

import uz.murodjon.uysotvoice.report.enums.ReportPeriodicity;

import java.time.Instant;

/**
 * A row of {@code GET/POST /api/reports/schedule...} (§10.10 "Jadval bo'yicha yuborish").
 *
 * @param email       where the report is sent
 * @param periodicity how often ({@link ReportPeriodicity#span()} is both the interval
 *                    and the window the attached report covers)
 * @param format      {@code csv}, {@code pdf}, or {@code xlsx} ({@link
 *                    uz.murodjon.uysotvoice.report.controller.ReportExportFactory})
 * @param campaignId  narrows the report to one campaign, or null for every campaign
 * @param enabled     paused schedules are skipped by the dispatch sweep, never deleted
 * @param lastSentAt  when the last email actually went out, or null if never
 */
public record ReportSchedule(
        long id,
        String email,
        ReportPeriodicity periodicity,
        String format,
        Long campaignId,
        boolean enabled,
        Instant lastSentAt,
        Instant createdAt
) {
}
