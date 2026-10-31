package uz.murodjon.robotcallv2.campaign.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignTargetEntity;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link CampaignTargetEntity}.
 */
@Repository
public interface CampaignTargetJpaRepository extends JpaRepository<CampaignTargetEntity, Long> {

    @Query("SELECT t FROM CampaignTargetEntity t WHERE t.id = :id AND t.company.id = :companyId")
    Optional<CampaignTargetEntity> findByIdAndCompanyId(@Param("id") long id, @Param("companyId") long companyId);

    List<CampaignTargetEntity> findByStatusAndNextAttemptAtBefore(TargetStatus status, Instant before);

    @Query("SELECT t FROM CampaignTargetEntity t WHERE t.campaign.id = :campaignId AND t.company.id = :companyId "
            + "AND t.phone = :phone AND t.status IN (:statuses) ORDER BY t.id")
    List<CampaignTargetEntity> findByCampaignIdAndPhoneAndStatusIn(@Param("companyId") long companyId,
                                                                   @Param("campaignId") long campaignId,
                                                                   @Param("phone") String phone,
                                                                   @Param("statuses") Collection<TargetStatus> statuses);

    @Query("SELECT t FROM CampaignTargetEntity t WHERE t.campaign.id = :campaignId AND t.company.id = :companyId")
    List<CampaignTargetEntity> findByCampaignIdAndCompanyId(@Param("campaignId") long campaignId,
                                                           @Param("companyId") long companyId,
                                                           Pageable pageable);

    @Query("SELECT count(t) FROM CampaignTargetEntity t WHERE t.campaign.id = :campaignId AND t.company.id = :companyId")
    long countByCampaignIdAndCompanyId(@Param("campaignId") long campaignId, @Param("companyId") long companyId);

    @Query("SELECT count(t) FROM CampaignTargetEntity t WHERE t.campaign.id = :campaignId AND t.status IN :statuses")
    long countByCampaignIdAndStatusIn(@Param("campaignId") long campaignId, @Param("statuses") List<TargetStatus> statuses);

    /** Rows of {@code [campaign id, targets]}: targets with a call that ended in one of {@code dispositions}. */
    @Query(value = "SELECT t.campaign_id, COUNT(DISTINCT t.id) FROM campaign_target t "
            + "JOIN call_attempt a ON a.target_id = t.id "
            + "WHERE t.campaign_id IN (:campaignIds) AND t.company_id = :companyId AND a.disposition IN (:dispositions) "
            + "GROUP BY t.campaign_id", nativeQuery = true)
    List<Object[]> countSuccessfulByCampaignIds(@Param("campaignIds") Collection<Long> campaignIds,
                                                @Param("companyId") long companyId,
                                                @Param("dispositions") Collection<String> dispositions);

    @Query("SELECT t.campaign.id AS campaignId, "
            + "COUNT(t) AS totalTargets, "
            + "COALESCE(SUM(CASE WHEN t.attempts > 0 OR t.status != uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus.PENDING THEN 1L ELSE 0L END), 0L) AS calledTargets, "
            + "COALESCE(SUM(CASE WHEN t.status = uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus.PENDING THEN 1L ELSE 0L END), 0L) AS pendingTargets, "
            + "COALESCE(SUM(CASE WHEN t.status = uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus.DONE THEN 1L ELSE 0L END), 0L) AS completedTargets "
            + "FROM CampaignTargetEntity t "
            + "WHERE t.campaign.id IN :campaignIds AND t.company.id = :companyId "
            + "GROUP BY t.campaign.id")
    List<CampaignTargetSummaryProjection> summarizeByCampaignIdsAndCompanyId(
            @Param("campaignIds") Collection<Long> campaignIds,
            @Param("companyId") long companyId);

    @Query("SELECT t.campaign.id AS campaignId, "
            + "COUNT(t) AS totalTargets, "
            + "COALESCE(SUM(CASE WHEN t.attempts > 0 OR t.status != uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus.PENDING THEN 1L ELSE 0L END), 0L) AS calledTargets, "
            + "COALESCE(SUM(CASE WHEN t.status = uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus.PENDING THEN 1L ELSE 0L END), 0L) AS pendingTargets, "
            + "COALESCE(SUM(CASE WHEN t.status = uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus.DONE THEN 1L ELSE 0L END), 0L) AS completedTargets "
            + "FROM CampaignTargetEntity t "
            + "WHERE t.campaign.id = :campaignId AND t.company.id = :companyId "
            + "GROUP BY t.campaign.id")
    Optional<CampaignTargetSummaryProjection> summarizeByCampaignIdAndCompanyId(
            @Param("campaignId") long campaignId,
            @Param("companyId") long companyId);

    @Query(value = "UPDATE campaign_target SET status = 'IN_PROGRESS', attempts = attempts + 1, step_attempts = step_attempts + 1, next_attempt_at = now() + INTERVAL '5 minute' "
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

    /**
     * {@link #claimDue} for a SCORE campaign: the most likely payers first, a target whose best
     * hour is {@code hour} lifted by {@code hourBonus}, an unscored one counted as {@code neutralScore}.
     */
    @Query(value = "UPDATE campaign_target SET status = 'IN_PROGRESS', attempts = attempts + 1, step_attempts = step_attempts + 1, next_attempt_at = now() + INTERVAL '5 minute' "
            + "WHERE id IN ("
            + "  SELECT t.id FROM campaign_target t"
            + "  WHERE t.campaign_id = :campaignId AND t.status = 'PENDING' AND t.do_not_call = false"
            + "    AND NOT EXISTS (SELECT 1 FROM do_not_call_list d "
            + "                    WHERE d.phone = t.phone AND d.company_id = t.company_id "
            + "                    AND d.removed_at IS NULL)"
            + "    AND (t.next_attempt_at IS NULL OR t.next_attempt_at <= now())"
            + "  ORDER BY COALESCE(t.score, :neutralScore)"
            + "           + CASE WHEN t.best_call_hour = :hour THEN :hourBonus ELSE 0 END DESC,"
            + "           t.next_attempt_at NULLS FIRST, t.id"
            + "  LIMIT :limit FOR UPDATE SKIP LOCKED"
            + ") RETURNING *", nativeQuery = true)
    List<CampaignTargetEntity> claimDueByScore(@Param("campaignId") long campaignId, @Param("limit") int limit,
                                               @Param("hour") int hour, @Param("hourBonus") int hourBonus,
                                               @Param("neutralScore") int neutralScore);

    /** The campaign's waiting targets never scored or scored before {@code scoredBefore}, by id. */
    @Query(value = "SELECT * FROM campaign_target WHERE campaign_id = :campaignId AND status = 'PENDING' "
            + "AND (scored_at IS NULL OR scored_at < :scoredBefore) ORDER BY scored_at NULLS FIRST, id LIMIT :limit",
            nativeQuery = true)
    List<CampaignTargetEntity> findToScore(@Param("campaignId") long campaignId,
                                           @Param("scoredBefore") Instant scoredBefore, @Param("limit") int limit);

    @Modifying
    @Transactional
    @Query(value = "UPDATE campaign_target SET score = :score, best_call_hour = :bestCallHour, scored_at = now() "
            + "WHERE id = :id AND company_id = :companyId", nativeQuery = true)
    void updateScore(@Param("companyId") long companyId, @Param("id") long id, @Param("score") int score,
                     @Param("bestCallHour") Integer bestCallHour);

    /**
     * {@link #claimDue} for one operator of a PREVIEW campaign: one target, stamped with who
     * holds it, on a lease long enough for the card to be read, dialled, talked through and
     * wrapped up before the expired-claim sweep may take it back.
     */
    @Query(value = "UPDATE campaign_target SET status = 'IN_PROGRESS', attempts = attempts + 1, step_attempts = step_attempts + 1, "
            + "next_attempt_at = now() + make_interval(mins => :leaseMinutes), claimed_by_user_id = :userId "
            + "WHERE id IN ("
            + "  SELECT t.id FROM campaign_target t"
            + "  WHERE t.campaign_id = :campaignId AND t.status = 'PENDING' AND t.do_not_call = false"
            + "    AND NOT EXISTS (SELECT 1 FROM do_not_call_list d "
            + "                    WHERE d.phone = t.phone AND d.company_id = t.company_id "
            + "                    AND d.removed_at IS NULL)"
            + "    AND (t.next_attempt_at IS NULL OR t.next_attempt_at <= now())"
            + "  ORDER BY t.next_attempt_at NULLS FIRST, t.id"
            + "  LIMIT 1 FOR UPDATE SKIP LOCKED"
            + ") RETURNING *", nativeQuery = true)
    List<CampaignTargetEntity> claimNextForOperator(@Param("campaignId") long campaignId, @Param("userId") long userId,
                                                    @Param("leaseMinutes") int leaseMinutes);

    /** The card an operator holds in a campaign: claimed by them and not settled yet. */
    @Query(value = "SELECT * FROM campaign_target WHERE campaign_id = :campaignId "
            + "AND claimed_by_user_id = :userId AND status = 'IN_PROGRESS' ORDER BY id LIMIT 1", nativeQuery = true)
    List<CampaignTargetEntity> findHeldByOperator(@Param("campaignId") long campaignId, @Param("userId") long userId);

    /** The inverse of one row of {@link #claimDue}: waiting again, and the attempt given back. */
    @Modifying
    @Transactional
    @Query(value = "UPDATE campaign_target SET status = 'PENDING', next_attempt_at = :nextAttemptAt, "
            + "attempts = GREATEST(attempts - 1, 0), step_attempts = GREATEST(step_attempts - 1, 0) WHERE id = :id", nativeQuery = true)
    void releaseClaim(@Param("id") long id, @Param("nextAttemptAt") Instant nextAttemptAt);

    @Modifying
    @Transactional
    @Query("UPDATE CampaignTargetEntity t SET t.status = :status, t.nextAttemptAt = :nextAttemptAt WHERE t.id = :id")
    void updateStatus(@Param("id") long id, @Param("status") TargetStatus status,
                      @Param("nextAttemptAt") Instant nextAttemptAt);

    @Modifying
    @Transactional
    @Query("UPDATE CampaignTargetEntity t SET t.doNotCall = true, t.status = uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus.DONE "
            + "WHERE t.id = :id AND t.company.id = :companyId")
    void setDoNotCall(@Param("id") long id, @Param("companyId") long companyId);

    @Modifying
    @Transactional
    @Query(value = "UPDATE campaign_target SET status = 'PENDING', attempts = 0, step_id = NULL, step_attempts = 0, "
            + "next_attempt_at = NULL WHERE campaign_id = :campaignId AND do_not_call = false", nativeQuery = true)
    void resetTargetsForRecurrence(@Param("campaignId") long campaignId);

    /** A target moves to {@code stepId} and waits there with no attempt spent on it yet. */
    @Modifying
    @Transactional
    @Query(value = "UPDATE campaign_target SET status = 'PENDING', step_id = :stepId, step_attempts = 0, "
            + "next_attempt_at = :nextAttemptAt WHERE id = :id", nativeQuery = true)
    void moveToStep(@Param("id") long id, @Param("stepId") String stepId,
                    @Param("nextAttemptAt") Instant nextAttemptAt);

    /** Targets never dialled — PENDING with no attempt spent; nothing points at them yet. */
    @Modifying
    @Transactional
    @Query("DELETE FROM CampaignTargetEntity t WHERE t.campaign.id = :campaignId AND t.company.id = :companyId "
            + "AND t.status = uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus.PENDING AND t.attempts = 0")
    int deleteUndialedByCampaignId(@Param("campaignId") long campaignId, @Param("companyId") long companyId);

    /** Targets waiting on a retry, closed as EXHAUSTED rather than deleted — their attempts are history. */
    @Modifying
    @Transactional
    @Query("UPDATE CampaignTargetEntity t SET t.status = uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus.EXHAUSTED "
            + "WHERE t.campaign.id = :campaignId AND t.company.id = :companyId "
            + "AND t.status = uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus.PENDING")
    int exhaustPendingByCampaignId(@Param("campaignId") long campaignId, @Param("companyId") long companyId);

    @Query("SELECT t.phone FROM CampaignTargetEntity t WHERE t.campaign.id = :campaignId AND t.company.id = :companyId "
            + "AND t.status IN (:statuses)")
    List<String> findPhonesByCampaignIdAndStatusIn(@Param("companyId") long companyId,
                                                   @Param("campaignId") long campaignId,
                                                   @Param("statuses") Collection<TargetStatus> statuses);

    /**
     * Unlike {@link #resetTargetsForRecurrence} this only drops the waiting time: the
     * attempt count stays, so a target that already used up its attempts is not called
     * again, and a target already dialled or finished is left alone.
     */
    @Modifying
    @Transactional
    @Query("UPDATE CampaignTargetEntity t SET t.nextAttemptAt = null "
            + "WHERE t.campaign.id = :campaignId AND t.doNotCall = false AND t.nextAttemptAt IS NOT NULL "
            + "AND t.status = uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus.PENDING")
    int clearSchedule(@Param("campaignId") long campaignId);
}
