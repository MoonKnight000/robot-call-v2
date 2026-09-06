package uz.murodjon.robotcallv2.campaign.presentation.dto;

import java.time.Instant;

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
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
