package uz.murodjon.uysotvoice.report.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import uz.murodjon.uysotvoice.report.enums.ReportPeriodicity;

/**
 * @param email       where to send the report ({@code POST /api/reports/schedule}, §10.10)
 * @param periodicity how often — also the window the attached report covers
 * @param format      {@code csv}, {@code pdf}, or {@code xlsx}; omit for {@code pdf}
 * @param campaignId  narrows the report to one campaign; omit for every campaign
 */
public record CreateReportScheduleRequest(
        @NotBlank @Email String email,
        @NotNull ReportPeriodicity periodicity,
        String format,
        Long campaignId
) {
}
