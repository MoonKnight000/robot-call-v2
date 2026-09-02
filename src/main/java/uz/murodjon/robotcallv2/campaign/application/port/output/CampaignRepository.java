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

    void updateStatus(long id, CampaignStatus status);

    void recordRecurrenceRun(long id, Instant lastRunAt, CampaignStatus status);

    void update(long id, Campaign row);
}
