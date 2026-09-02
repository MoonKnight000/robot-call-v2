package uz.murodjon.robotcallv2.campaign.application.port.output;

import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetFilter;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;

import java.time.Instant;
import java.util.List;

public interface CampaignTargetRepository {

    long add(long campaignId, long clientId, String phone, String language, String contextDataJson);

    CampaignTarget find(long id);

    void updateContextData(long id, String contextDataJson);

    void resetTargetsForRecurrence(long campaignId);

    List<CampaignTarget> findByCampaign(long campaignId, TargetFilter filter);

    long countByCampaign(long campaignId);

    long countActive(long campaignId);

    List<CampaignTarget> claimDue(long campaignId, int limit);

    void updateStatus(long id, TargetStatus status, Instant nextAttemptAt);

    void setDoNotCall(long id);
}
