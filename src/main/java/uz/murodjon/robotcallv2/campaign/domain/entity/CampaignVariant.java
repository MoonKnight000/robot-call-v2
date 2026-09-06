package uz.murodjon.robotcallv2.campaign.domain.entity;

import java.time.Instant;

/**
 * Domain entity representing an A/B testing variant within a campaign.
 */
public record CampaignVariant(
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
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public double conversionRate() {
        if (answeredCount <= 0) {
            return 0.0;
        }
        return Math.round(((double) convertedCount / answeredCount) * 10000.0) / 100.0;
    }

    public double answerRate() {
        if (callsCount <= 0) {
            return 0.0;
        }
        return Math.round(((double) answeredCount / callsCount) * 10000.0) / 100.0;
    }
}
