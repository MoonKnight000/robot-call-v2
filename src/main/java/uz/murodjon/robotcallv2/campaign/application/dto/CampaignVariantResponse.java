package uz.murodjon.robotcallv2.campaign.application.dto;

import java.time.Instant;

/**
 * One A/B variant, on two sets of numbers.
 *
 * <p>{@code convertedCount} and {@code conversionRate} count the dispositions the bot
 * recorded — a promise to pay. {@code attributedConversions} and
 * {@code attributedValueUzs} count what a company's own systems later reported actually
 * happening (conversions.md). Both are shown because they answer different questions, and
 * a company that has configured no goals still has the first.
 *
 * @param spentUzs             what this variant's settled calls were charged
 * @param costPerConversionUzs spend divided by real conversions, or null when there are none
 */
public record CampaignVariantResponse(
        long id,
        long campaignId,
        long companyId,
        String name,
        Long aiAgentId,
        String promptOverride,
        String ttsVoiceId,
        int trafficWeight,
        int callsCount,
        int answeredCount,
        int convertedCount,
        double answerRate,
        double conversionRate,
        long attributedConversions,
        long attributedValueUzs,
        long spentUzs,
        Long costPerConversionUzs,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
