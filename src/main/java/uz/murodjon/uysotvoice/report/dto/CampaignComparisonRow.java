package uz.murodjon.uysotvoice.report.dto;

/** One campaign's KPIs for side-by-side comparison (§10.10 Grafik 4). */
public record CampaignComparisonRow(
        long campaignId,
        String campaignName,
        long totalCalls,
        long answeredCalls,
        double answerRate,
        Double avgDurationSec,
        long promises
) {
}
