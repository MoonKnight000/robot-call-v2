package uz.murodjon.robotcallv2.report.application.dto;

/** One line of {@code GET /api/reports/campaign-comparison}. */
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
