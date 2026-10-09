package uz.murodjon.robotcallv2.dialer.application.dto;

import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;

/**
 * The client an operator of a PREVIEW campaign is to call next
 * ({@code POST /api/operator/campaigns/{campaignId}/next}).
 *
 * @param callId the call already placed for this card, or null while it has not been dialled.
 *               Set, the card waits for that call's wrap-up and can no longer be skipped
 * @param memory what the company knows about the number from earlier calls, or null
 */
public record PreviewCardResponse(
        CampaignTarget target,
        String campaignName,
        Long callId,
        ClientMemory memory) {
}
