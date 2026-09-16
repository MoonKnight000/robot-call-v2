package uz.murodjon.robotcallv2.campaign.application.port.output;

import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTargetStats;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetFilter;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface CampaignTargetRepository {

    /**
     * @return the new target's id, or {@code 0} when the campaign already has this number
     *         queued (PENDING / IN_PROGRESS) — the row is left as it is
     */
    long add(long companyId, CampaignTarget target);

    /** The campaign's queued (PENDING / IN_PROGRESS) target for {@code phone}, or null. */
    CampaignTarget findQueued(long companyId, long campaignId, String phone);

    CampaignTarget find(long companyId, long id);

    void resetTargetsForRecurrence(long campaignId);

    List<CampaignTarget> findByCampaign(long companyId, long campaignId, TargetFilter filter);

    long countByCampaign(long companyId, long campaignId);

    long countActive(long campaignId);

    List<CampaignTarget> claimDue(long campaignId, int limit);

    /**
     * Drops the booked next-attempt time of every waiting target, making them due at once.
     *
     * @return how many targets were released
     */
    int clearSchedule(long campaignId);

    void updateStatus(long id, TargetStatus status, Instant nextAttemptAt);

    /**
     * Undo a claim without spending the attempt: back to PENDING at {@code nextAttemptAt},
     * with the attempt {@code claimDue} counted taken back. For a target the dialer claimed
     * and then decided not to dial — nothing was placed, so nothing may be counted.
     */
    void releaseClaim(long id, Instant nextAttemptAt);

    /**
     * IN_PROGRESS targets whose claim lease ({@link #claimDue} sets next_attempt_at to
     * now + 5 min) ran out before {@code claimedBefore}, as target id → company id.
     */
    Map<Long, Long> findCompanyIdsOfExpiredClaims(Instant claimedBefore);

    void setDoNotCall(long companyId, long id);

    /** For a source that replaces the list: the targets never dialled (PENDING, no attempt spent). */
    int deleteUndialedByCampaignId(long companyId, long campaignId);

    /** For a source that replaces the list: targets waiting on a retry, closed as EXHAUSTED. */
    int exhaustPendingByCampaignId(long companyId, long campaignId);

    /** The numbers this campaign has queued (PENDING / IN_PROGRESS), read once for a bulk import. */
    Set<String> findQueuedPhones(long companyId, long campaignId);

    /**
     * Inserts targets the caller already knows are not queued, in as few statements as the
     * batch allows. Every phone in {@code targets} must be distinct.
     *
     * @return phone → id of every row inserted; a phone missing from the map was found queued
     *         after all (the unique index has the last word) and nothing was written for it
     */
    Map<String, Long> insertAllQueued(long companyId, List<CampaignTarget> targets);

    Map<Long, CampaignTargetStats> findStatsByCampaignIds(long companyId, Collection<Long> campaignIds);

    CampaignTargetStats findStatsByCampaignId(long companyId, long campaignId);
}
