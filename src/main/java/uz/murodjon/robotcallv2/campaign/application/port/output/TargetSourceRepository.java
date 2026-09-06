package uz.murodjon.robotcallv2.campaign.application.port.output;

import uz.murodjon.robotcallv2.campaign.domain.entity.TargetSource;

import java.time.Instant;

public interface TargetSourceRepository {

    TargetSource findByCampaignId(long campaignId);

    TargetSource upsert(long campaignId, TargetSource source);

    void delete(long campaignId);

    /** Stamps the outcome of a fetch; {@code error} null means it succeeded. */
    void recordSync(long campaignId, Instant syncedAt, int added, String error);
}
