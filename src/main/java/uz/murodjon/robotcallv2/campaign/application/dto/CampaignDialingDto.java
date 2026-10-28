package uz.murodjon.robotcallv2.campaign.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignDialing;
import uz.murodjon.robotcallv2.campaign.domain.enums.DialMode;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetOrdering;

/**
 * {@link CampaignDialing} as the campaign endpoints read and write it.
 *
 * @param mode                 who talks to the clients: AI (default), PREVIEW, PROGRESSIVE or
 *                             PREDICTIVE. Fixed once the campaign exists — an edit ignores it
 * @param callQueueId          the operator queue whose members take the calls — required for every
 *                             mode but AI
 * @param maxAiOverflowPercent PREDICTIVE: 0–100, default 20 — see {@code CampaignDialing}
 * @param targetOrdering       which due targets are dialled first: FIFO (default) or SCORE
 */
public record CampaignDialingDto(
        DialMode mode,
        Long callQueueId,
        @Min(0) @Max(100) Integer maxAiOverflowPercent,
        TargetOrdering targetOrdering
) {

    public static CampaignDialingDto of(CampaignDialing dialing) {
        return new CampaignDialingDto(dialing.mode(), dialing.callQueueId(), dialing.maxAiOverflowPercent(),
                dialing.targetOrdering());
    }
}
