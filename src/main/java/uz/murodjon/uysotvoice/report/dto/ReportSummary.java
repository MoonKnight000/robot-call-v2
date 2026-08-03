package uz.murodjon.uysotvoice.report.dto;

import java.time.Instant;
import java.util.List;

/**
 * The whole Reports page bundled into one downloadable file (§10.10 "⇩ Hisobotni
 * yuklab olish") — the KPI totals, the outcome breakdown, the funnel, and the
 * per-campaign comparison, all over the same {@code [from, to)} window.
 *
 * @param from       start of the window
 * @param to         end of the window
 * @param campaignId narrows every section to one campaign, or null for every campaign
 * @param totals     the 4 KPI-card scalars (§10.2)
 * @param outcomes   disposition distribution (Grafik 2)
 * @param funnel     Qo'ng'iroq -> Javob -> Shaxs tasdiqlandi -> Suhbat -> Natija (Grafik 6)
 * @param campaigns  per-campaign comparison (Grafik 4) — empty when {@code campaignId}
 *                   already narrows the report to one campaign
 */
public record ReportSummary(
        Instant from,
        Instant to,
        Long campaignId,
        DashboardTotals totals,
        List<DashboardOutcome> outcomes,
        List<FunnelStage> funnel,
        List<CampaignComparisonRow> campaigns
) {
}
