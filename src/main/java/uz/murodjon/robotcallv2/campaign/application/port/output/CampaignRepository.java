package uz.murodjon.robotcallv2.campaign.application.port.output;

import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignFilter;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;

import java.time.Instant;
import java.util.List;

public interface CampaignRepository {

    long create(long companyId, Campaign row);

    Campaign find(long companyId, long id);

    List<Campaign> findAll(long companyId, CampaignFilter filter);

    long count(long companyId);

    long count(long companyId, CampaignFilter filter);

    List<Campaign> searchByName(long companyId, String q, int limit);

    List<Campaign> findActive();

    List<Campaign> findRecurring();

    void updateStatus(long companyId, long id, CampaignStatus status);

    void recordRecurrenceRun(long id, Instant lastRunAt, CampaignStatus status);

    void update(long companyId, long id, Campaign row);
}
