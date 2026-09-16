package uz.murodjon.robotcallv2.campaign.domain.entity;

import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;

import java.time.Instant;

/**
 * Domain entity representing an A/B testing variant within a campaign.
 *
 * @param pipelineMode the pipeline this variant's calls run on, over the agent's own;
 *                     null keeps the agent's. What makes cascade-versus-realtime a
 *                     measurable test instead of two campaigns on two different weeks
 */
public record CampaignVariant(
        long id,
        long campaignId,
        long companyId,
        String name,
        Long aiAgentId,
        String promptOverride,
        String ttsVoiceId,
        PipelineMode pipelineMode,
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
