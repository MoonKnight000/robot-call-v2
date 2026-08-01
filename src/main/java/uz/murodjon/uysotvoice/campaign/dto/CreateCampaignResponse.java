package uz.murodjon.uysotvoice.campaign.dto;

import uz.murodjon.uysotvoice.campaign.enums.CampaignStatus;

public record CreateCampaignResponse(long id, CampaignStatus status) {
}
