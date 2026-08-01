package uz.murodjon.uysotvoice.campaign.dto;

import uz.murodjon.uysotvoice.campaign.enums.TargetStatus;

/** A row of {@code campaign_target} (PROJECT.md §6). */
public record TargetRow(
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
