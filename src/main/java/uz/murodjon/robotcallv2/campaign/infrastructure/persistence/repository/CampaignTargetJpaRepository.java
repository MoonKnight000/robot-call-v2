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

    Optional<CampaignTargetEntity> findFirstByPhoneOrderById(String phone);

    @Query("SELECT t FROM CampaignTargetEntity t WHERE t.campaign.id = :campaignId AND t.company.id = :companyId")
    List<CampaignTargetEntity> findByCampaignIdAndCompanyId(@Param("campaignId") long campaignId,
                                                           @Param("companyId") long companyId,
                                                           Pageable pageable);

    @Query("SELECT count(t) FROM CampaignTargetEntity t WHERE t.campaign.id = :campaignId AND t.company.id = :companyId")
    long countByCampaignIdAndCompanyId(@Param("campaignId") long campaignId, @Param("companyId") long companyId);

    @Query("SELECT count(t) FROM CampaignTargetEntity t WHERE t.campaign.id = :campaignId AND t.status IN :statuses")
    long countByCampaignIdAndStatusIn(@Param("campaignId") long campaignId, @Param("statuses") List<TargetStatus> statuses);

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

    @Query(value = "UPDATE campaign_target SET status = 'IN_PROGRESS', attempts = attempts + 1, next_attempt_at = now() + INTERVAL '5 minute' "
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
    @Query("UPDATE CampaignTargetEntity t SET t.status = uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus.PENDING, t.attempts = 0, t.nextAttemptAt = null WHERE t.campaign.id = :campaignId AND t.doNotCall = false")
    void resetTargetsForRecurrence(@Param("campaignId") long campaignId);

    @Modifying
    @Transactional
    @Query("DELETE FROM CampaignTargetEntity t WHERE t.campaign.id = :campaignId AND t.company.id = :companyId")
    int deleteByCampaignId(@Param("campaignId") long campaignId, @Param("companyId") long companyId);

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
