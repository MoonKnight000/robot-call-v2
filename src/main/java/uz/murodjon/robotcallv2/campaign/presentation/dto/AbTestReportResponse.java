package uz.murodjon.robotcallv2.campaign.presentation.dto;

import java.util.List;

/**
 * @param leadingVariantName    the variant with the best conversion rate so far — a
 *                              ranking, not a verdict
 * @param winningVariantName    the variant that has actually won, or null while the test is
 *                              still inconclusive; see
 *                              {@link uz.murodjon.robotcallv2.campaign.domain.service.AbTestSignificance}
 * @param conversionRateLow     lower edge of the 95% interval around
 *                              {@code overallConversionRate}, in percent
 * @param conversionRateHigh    upper edge of the same interval, in percent
 */
public record AbTestReportResponse(
        long campaignId,
        long companyId,
        int totalVariants,
        int totalCalls,
        int totalAnswered,
        int totalConverted,
        double overallConversionRate,
        double conversionRateLow,
        double conversionRateHigh,
        String leadingVariantName,
        String winningVariantName,
        List<CampaignVariantResponse> variants
) {
}
