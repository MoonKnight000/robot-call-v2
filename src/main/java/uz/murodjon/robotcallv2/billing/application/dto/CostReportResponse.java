package uz.murodjon.robotcallv2.billing.application.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /api/billing/costs} — everything a company spent in a period, line by line
 * (docs/api/billing.md).
 *
 * @param from       first day of the period (Tashkent), inclusive
 * @param to         last day of the period, inclusive
 * @param totalUzs   the sum of {@code components}
 * @param usage      what the call lines were priced on
 * @param components every cost line, zero lines included, in a fixed order
 * @param rates      the price list in force now, as the company pays it
 * @param daily      spend per day, every day of the period present
 * @param models     LLM cost per model, most expensive first — from the per-model lines (llm_usage),
 *                   which exist only for charges made since they were introduced
 */
public record CostReportResponse(
        LocalDate from,
        LocalDate to,
        long totalUzs,
        CostUsageDto usage,
        List<CostComponentDto> components,
        CostRatesDto rates,
        List<DailySpendDto> daily,
        List<ModelCostDto> models
) {
}
