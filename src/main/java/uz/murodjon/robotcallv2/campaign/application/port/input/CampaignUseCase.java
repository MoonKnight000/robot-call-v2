package uz.murodjon.robotcallv2.campaign.application.port.input;

import uz.murodjon.robotcallv2.campaign.application.dto.*;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;

public interface CampaignUseCase {

    CreateCampaignResponse createCampaign(long companyId, CreateCampaignRequest request);

    CampaignRow updateCampaign(long companyId, long id, UpdateCampaignRequest request);

    CampaignRow clone(long companyId, long id);

    CampaignRow campaignRow(long companyId, long id);

    Campaign getCampaign(long companyId, long id);

    Campaign requireCampaign(long companyId, long id);

    PageableData<CampaignRow> filterCampaigns(long companyId, CampaignFilter filter);

    PageableData<CampaignRow> listCampaigns(long companyId, CampaignFilter filter);

    CampaignStatusResponse start(long companyId, long campaignId, boolean immediate);

    CampaignStatusResponse pause(long companyId, long campaignId);

    CampaignStatusResponse archive(long companyId, long campaignId);

    void triggerRecurrenceRun(long companyId, long campaignId, boolean resetTargets);
}
