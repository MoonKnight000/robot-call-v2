package uz.murodjon.robotcallv2.dialer.application.port.input;

import uz.murodjon.robotcallv2.dialer.application.dto.CampaignCapacityResponse;

/** How many calls a campaign may hold at once and start per second, within its company's capacity. */
public interface CampaignCapacityUseCase {

    CampaignCapacityResponse findCampaignCapacity(long companyId, long campaignId);
}
