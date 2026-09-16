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

    /**
     * A target queued for dialling. Identity is the storage layer's, and a new target
     * always starts PENDING with no attempts spent and no do-not-call mark.
     */
    public static CampaignTarget queued(long campaignId, long clientId, String phone, String language,
                                        String contextData) {
        return queued(campaignId, clientId, phone, language, contextData, false);
    }

    /**
     * The same, with the do-not-call mark decided at import: a number on the company's
     * list is still stored — the campaign's counts should show it — but never dialled.
     */
    public static CampaignTarget queued(long campaignId, long clientId, String phone, String language,
                                        String contextData, boolean doNotCall) {
        return new CampaignTarget(0, campaignId, clientId, phone, language, contextData,
                TargetStatus.PENDING, 0, doNotCall);
    }
}
