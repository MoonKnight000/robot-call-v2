package uz.murodjon.uysotvoice.campaign.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.uysotvoice.campaign.entity.CampaignTargetEntity;
import uz.murodjon.uysotvoice.campaign.enums.TargetStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Spring Data repository for {@link CampaignTargetEntity}. */
@Repository
public interface CampaignTargetJpaRepository extends JpaRepository<CampaignTargetEntity, Long> {

    Optional<CampaignTargetEntity> findByIdAndCompanyId(long id, long companyId);

    /** The placeholder target seeded by {@code V2} (phone = 'MANUAL'); see {@code CallRecordService.manualTargetId}. */
    Optional<CampaignTargetEntity> findFirstByPhoneOrderById(String phone);

    List<CampaignTargetEntity> findByCampaign_IdAndCompanyId(long campaignId, long companyId, Pageable pageable);

    long countByCampaign_IdAndCompanyId(long campaignId, long companyId);

    /** Targets still in the dial loop (not yet DONE/FAILED/EXHAUSTED) — used to detect campaign completion. */
    long countByCampaign_IdAndStatusIn(long campaignId, List<TargetStatus> statuses);

    /**
     * Atomically claim up to {@code limit} targets that are ready to dial: PENDING,
     * not opted out (the per-target flag <em>and</em> the phone-level list from
     * §11.4), and past their retry time. Claimed rows come back already marked
     * IN_PROGRESS with the attempt counted.
     *
     * <p>Select-then-update in two statements would let a second dialer instance — or
     * a tick that overruns its interval — pick the same target and call the client
     * twice. {@code FOR UPDATE SKIP LOCKED} hands each row to exactly one claimer and
     * lets the others move on instead of blocking.
     *
     * <p>No {@code @Modifying}: this returns rows via {@code RETURNING *}, so Spring
     * Data must run it as a query ({@code getResultList()}) rather than as an update
     * ({@code executeUpdate()}), even though the statement is an UPDATE.
     *
     * <p>Deliberately not scoped by company — the dialer ticks every company's active
     * campaigns in turn; {@code campaignId} alone already pins this to one company's
     * data. The opt-out check is still company-aware: a phone on company A's
     * do-not-call list must not block company B's campaign, so it joins on the
     * target's own {@code company_id} rather than matching by phone alone (§B.2). A
     * removed ("Ro'yxatdan chiqarish", §10.8) opt-out no longer blocks either —
     * {@code removed_at IS NULL} is what makes the removal actually let dialling resume.
     */
    @Query(value = "UPDATE campaign_target SET status = 'PENDING', attempts = attempts + 1 "
            + "WHERE id IN ("
            + "  SELECT t.id FROM campaign_target t"
            + "  WHERE t.campaign_id = :campaignId AND t.status = 'PENDING' AND t.do_not_call = false"
            + "    AND NOT EXISTS (SELECT 1 FROM do_not_call_list d "
            + "                    WHERE d.phone = t.phone AND d.company_id = t.company_id "
            + "                    AND d.removed_at IS NULL)"
            + "    AND (t.next_attempt_at IS NULL OR t.next_attempt_at <= now())"
            + "  ORDER BY t.next_attempt_at NULLS FIRST, t.id"
            + "  LIMIT :limit FOR UPDATE SKIP LOCKED"
            + ") RETURNING *", nativeQuery = true)
    List<CampaignTargetEntity> claimDue(@Param("campaignId") long campaignId, @Param("limit") int limit);

    /** Internal (dialer outcome application) — not scoped, see {@link #claimDue}. */
    @Modifying @Transactional
    @Query("UPDATE CampaignTargetEntity t SET t.status = :status, t.nextAttemptAt = :nextAttemptAt WHERE t.id = :id")
    void updateStatus(@Param("id") long id, @Param("status") TargetStatus status,
                      @Param("nextAttemptAt") Instant nextAttemptAt);

    /** Scoped to the current company — reached by a bare target id, see {@link #findByIdAndCompanyId}. */
    @Modifying
    @Transactional
    @Query("UPDATE CampaignTargetEntity t SET t.doNotCall = true, t.status = uz.murodjon.uysotvoice.campaign.enums.TargetStatus.DONE "
            + "WHERE t.id = :id AND t.companyId = :companyId")
    void setDoNotCall(@Param("id") long id, @Param("companyId") long companyId);

    @Modifying
    @Transactional
    @Query("UPDATE CampaignTargetEntity t SET t.contextData = :contextData WHERE t.id = :id AND t.companyId = :companyId")
    void updateContextData(@Param("id") long id, @Param("companyId") long companyId, @Param("contextData") String contextData);

    @Modifying
    @Transactional
    @Query("UPDATE CampaignTargetEntity t SET t.status = uz.murodjon.uysotvoice.campaign.enums.TargetStatus.PENDING, t.attempts = 0, t.nextAttemptAt = null WHERE t.campaign.id = :campaignId AND t.doNotCall = false")
    void resetTargetsForRecurrence(@Param("campaignId") long campaignId);
}
