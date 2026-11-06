package uz.murodjon.robotcallv2.billing.application.dto;

import java.time.LocalDate;

/**
 * Spend on one day of {@link CostReportResponse}'s period.
 *
 * @param date the day (Tashkent)
 * @param uzs  calls and extra AI charges together
 */
public record DailySpendDto(LocalDate date, long uzs) {
}
