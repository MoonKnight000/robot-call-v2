package uz.murodjon.robotcallv2.campaign.application.port.input;

import uz.murodjon.robotcallv2.campaign.application.dto.*;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;

public interface CampaignUseCase {

    CreateCampaignResponse createCampaign(CreateCampaignRequest r);

    CampaignRow updateCampaign(long id, UpdateCampaignRequest r);

    CampaignRow clone(long id);

    CampaignRow campaignRow(long id);

    Campaign getCampaign(long id);

    Campaign requireCampaign(long id);

    PageableData<CampaignRow> filterCampaigns(CampaignFilter filter);

    PageableData<CampaignRow> listCampaigns(CampaignFilter filter);

    CampaignStatusResponse start(long campaignId, boolean immediate);

    CampaignStatusResponse pause(long campaignId);

    CampaignStatusResponse archive(long campaignId);

    void triggerRecurrenceRun(long campaignId, boolean resetTargets);
}
