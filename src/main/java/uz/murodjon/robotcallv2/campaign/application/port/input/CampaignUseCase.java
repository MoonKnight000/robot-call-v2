package uz.murodjon.robotcallv2.campaign.application.port.input;

import uz.murodjon.robotcallv2.campaign.application.dto.*;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.List;

public interface CampaignUseCase {

    CreateCampaignResponse createCampaign(long companyId, CreateCampaignRequest request);

    CampaignRow updateCampaign(long companyId, long id, UpdateCampaignRequest request);

    CampaignRow clone(long companyId, long id);

    /** The campaign as the campaigns page shows it — 404 when the company has no such campaign. */
    CampaignRow requireCampaignRow(long companyId, long id);

    /** The company's campaign, or null when it has none by that id. */
    Campaign findCampaign(long companyId, long id);

    Campaign requireCampaign(long companyId, long id);

    PageableData<CampaignRow> filterCampaigns(long companyId, CampaignFilter filter);

    PageableData<CampaignRow> listCampaigns(long companyId, CampaignFilter filter);

    /**
     * Records that the contacts the campaign calls were gathered lawfully (consent, do-not-call
     * laws, opt-ins). A DRAFT cannot be launched before this; asking again changes nothing.
     */
    CampaignRow acceptConsent(long companyId, long campaignId);

    /** The Overview tab: the campaign, its caller ID pool and what, if anything, blocks a launch. */
    CampaignOverviewResponse requireCampaignOverview(long companyId, long campaignId);

    CampaignStatusResponse start(long companyId, long campaignId, boolean immediate);

    CampaignStatusResponse pause(long companyId, long campaignId);

    CampaignStatusResponse archive(long companyId, long campaignId);

    void triggerRecurrenceRun(long companyId, long campaignId, boolean resetTargets);

    /**
     * The dialer found the campaign cannot place a call at all and parks it, telling the owner
     * why. Called off a scheduler thread, for any company; a campaign that is no longer
     * ACTIVE is left as it is.
     */
    void pauseByPlatform(long companyId, long campaignId, String reason);

    /** Tells the owner the dialer is holding this campaign's calls for want of balance. */
    void notifyBalanceExhausted(long companyId, long campaignId);

    /**
     * Activates SCHEDULED campaigns whose startAt has come and completes ACTIVE ones whose
     * endAt has passed. Run from the recurrence sweep, for every company.
     */
    void sweepScheduledCampaigns();

    /** Every company's ACTIVE campaigns — what the dialer walks on each tick. */
    List<Campaign> findActiveCampaigns();

    List<Campaign> findCampaignsByName(long companyId, String query, int limit);
}
