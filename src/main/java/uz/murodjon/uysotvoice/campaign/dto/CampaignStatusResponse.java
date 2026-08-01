package uz.murodjon.uysotvoice.campaign.dto;

import uz.murodjon.uysotvoice.campaign.enums.CampaignStatus;

public record CampaignStatusResponse(long campaignId, CampaignStatus status) {
}
