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

    long countByCompanyId(long companyId);

    long countByFilter(long companyId, CampaignFilter filter);

    List<Campaign> searchByName(long companyId, String q, int limit);

    /** ACTIVE campaigns of companies that are not suspended — what the dialer may dial now. */
    List<Campaign> findActive();

    List<Campaign> findRecurring();

    /** SCHEDULED campaigns whose startAt has come. */
    List<Campaign> findScheduledDue(Instant now);

    /** ACTIVE campaigns whose endAt has passed. */
    List<Campaign> findActiveExpired(Instant now);

    void updateStatus(long companyId, long id, CampaignStatus status);

    /**
     * ACTIVE → COMPLETED in one statement, only when no target is still PENDING or
     * IN_PROGRESS; whichever concurrent caller gets {@code true} announces it.
     */
    boolean completeIfFinished(long companyId, long id);

    /** Stamps the consent confirmation at {@code at}; a campaign that already has one keeps its own. */
    void recordConsent(long companyId, long id, Instant at);

    void recordRecurrenceRun(long id, Instant lastRunAt, CampaignStatus status);

    /**
     * Writes the editable fields of {@code row}, only while the stored campaign is still at
     * {@code row.version()}.
     *
     * @return {@code false} when someone saved the campaign after it was read — nothing is
     *         written; {@code true} otherwise, including for a campaign that no longer exists
     */
    boolean update(long companyId, long id, Campaign row);
}
