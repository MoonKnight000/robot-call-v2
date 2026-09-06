package uz.murodjon.robotcallv2.campaign.application.port.output;

import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTargetStats;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetFilter;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface CampaignTargetRepository {

    long add(long campaignId, long clientId, String phone, String language, String contextDataJson);

    CampaignTarget find(long id);

    void resetTargetsForRecurrence(long campaignId);

    List<CampaignTarget> findByCampaign(long campaignId, TargetFilter filter);

    long countByCampaign(long campaignId);

    long countActive(long campaignId);

    List<CampaignTarget> claimDue(long campaignId, int limit);

    /**
     * Drops the booked next-attempt time of every waiting target, making them due at once.
     *
     * @return how many targets were released
     */
    int clearSchedule(long campaignId);

    void updateStatus(long id, TargetStatus status, Instant nextAttemptAt);

    void setDoNotCall(long id);

    /** Clears the whole list, for a source that answers with the whole of today's. */
    int deleteByCampaignId(long campaignId);

    Map<Long, CampaignTargetStats> statsByCampaignIds(Collection<Long> campaignIds);

    CampaignTargetStats statsByCampaignId(long campaignId);
}
