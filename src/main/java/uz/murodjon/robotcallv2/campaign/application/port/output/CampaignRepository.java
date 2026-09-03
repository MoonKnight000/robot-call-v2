package uz.murodjon.robotcallv2.campaign.application.port.output;

import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignFilter;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;

import java.time.Instant;
import java.util.List;

public interface CampaignRepository {

    long create(Campaign row);

    Campaign find(long id);

    List<Campaign> findAll(CampaignFilter filter);

    long count();

    long count(CampaignFilter filter);

    List<Campaign> searchByName(String q, int limit);

    List<Campaign> findActive();

    List<Campaign> findRecurring();

    /**
     * The company is an argument, not ambient state: the dialer changes a campaign's
     * status from a scheduled thread, where the request-scoped current company resolves
     * to the platform default and would scope the update to the wrong tenant.
     */
    void updateStatus(long companyId, long id, CampaignStatus status);

    void recordRecurrenceRun(long id, Instant lastRunAt, CampaignStatus status);

    void update(long id, Campaign row);
}
