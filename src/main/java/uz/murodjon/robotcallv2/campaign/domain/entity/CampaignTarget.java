package uz.murodjon.robotcallv2.campaign.domain.entity;

import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;

public record CampaignTarget(
        long id,
        long campaignId,
        long clientId,
        String phone,
        String language,
        String contextData,
        TargetStatus status,
        int attempts,
        boolean doNotCall
) {
}
