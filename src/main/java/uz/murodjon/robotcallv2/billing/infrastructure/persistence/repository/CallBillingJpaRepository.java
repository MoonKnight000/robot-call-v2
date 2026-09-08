package uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.murodjon.robotcallv2.billing.domain.enums.CallBillingStatus;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.CallBillingEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CallBillingJpaRepository extends JpaRepository<CallBillingEntity, Long> {

    Optional<CallBillingEntity> findByCallAttemptId(Long callAttemptId);

    /**
     * The oldest hold still open for a target — the one the call now finishing was
     * dialled under. Oldest first, because a retried target can have taken a fresh hold
     * while an earlier one is still being settled.
     */
    Optional<CallBillingEntity> findFirstByCompanyIdAndTargetIdAndStatusOrderByCreatedAtAsc(
            Long companyId, Long targetId, CallBillingStatus status);

    /**
     * What a company spent per month, newest month first — one row per month with the
     * charged total and the minutes behind it.
     */
    @Query(value = """
            SELECT to_char(settled_at AT TIME ZONE 'Asia/Tashkent', 'YYYY-MM') AS period,
                   COALESCE(SUM(total_uzs), 0)                                 AS spend_uzs,
                   COALESCE(SUM(duration_sec), 0)                              AS duration_sec
              FROM call_billing
             WHERE company_id = :companyId
               AND status = 'SETTLED'
             GROUP BY period
             ORDER BY period DESC
             LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findMonthlySpend(@Param("companyId") Long companyId, @Param("limit") int limit);

    /**
     * Charged calls of one campaign, grouped by the A/B variant that ran them. The join
     * to call_attempt is what carries the variant: a charge knows its call, and the call
     * is what remembers which script it spoke.
     */
    @Query(value = """
            SELECT ca.variant_id                       AS variant_id,
                   COUNT(*)                            AS calls,
                   COALESCE(SUM(cb.total_uzs), 0)      AS spent_uzs
              FROM call_billing cb
              JOIN call_attempt ca ON ca.id = cb.call_attempt_id
              JOIN campaign_target ct ON ct.id = ca.target_id
             WHERE cb.company_id = :companyId
               AND cb.status = 'SETTLED'
               AND ct.campaign_id = :campaignId
             GROUP BY ca.variant_id
            """, nativeQuery = true)
    List<Object[]> findSpendByVariant(@Param("companyId") Long companyId, @Param("campaignId") Long campaignId);

    /** The charged totals and usage for one period, as one row. */
    @Query(value = """
            SELECT COALESCE(SUM(duration_sec), 0)                        AS duration_sec,
                   COALESCE(SUM(prompt_tokens + completion_tokens), 0)   AS tokens,
                   COALESCE(SUM(tts_chars), 0)                           AS tts_chars,
                   COALESCE(SUM(total_uzs), 0)                           AS spend_uzs
              FROM call_billing
             WHERE company_id = :companyId
               AND status = 'SETTLED'
               AND settled_at >= :from
            """, nativeQuery = true)
    List<Object[]> findUsageSince(@Param("companyId") Long companyId, @Param("from") Instant from);
}
