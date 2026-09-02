package uz.murodjon.robotcallv2.campaign.application.dto;

import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;

public record CampaignStatusResponse(long campaignId, CampaignStatus status) {
}
