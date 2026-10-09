package uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
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

    /**
     * What one campaign's settled calls were charged since {@code since}, all of it when
     * null — the one query behind the dialer's budget cap and the campaign report's spend.
     */
    @Query(value = """
            SELECT COALESCE(SUM(cb.total_uzs), 0)
              FROM call_billing cb
              JOIN call_attempt ca ON ca.id = cb.call_attempt_id
              JOIN campaign_target ct ON ct.id = ca.target_id
             WHERE cb.company_id = :companyId
               AND cb.status = 'SETTLED'
               AND ct.campaign_id = :campaignId
               AND (CAST(:since AS timestamptz) IS NULL OR cb.settled_at >= :since)
            """, nativeQuery = true)
    long sumSpentUzsByCampaign(@Param("companyId") long companyId, @Param("campaignId") long campaignId,
                               @Param("since") Instant since);

    /** The charged totals and usage for one period, as one row. */
    @Query(value = """
            SELECT COALESCE(SUM(duration_sec), 0)                        AS duration_sec,
                   COALESCE(SUM(prompt_tokens + completion_tokens
                                + summary_prompt_tokens + summary_completion_tokens), 0) AS tokens,
                   COALESCE(SUM(tts_chars), 0)                           AS tts_chars,
                   COALESCE(SUM(total_uzs), 0)                           AS spend_uzs
              FROM call_billing
             WHERE company_id = :companyId
               AND status = 'SETTLED'
               AND settled_at >= :from
            """, nativeQuery = true)
    List<Object[]> findUsageSince(@Param("companyId") Long companyId, @Param("from") Instant from);

    /** The post-call summary, added to the call's settled row (V5). */
    @Modifying @Transactional
    @Query(value = """
            UPDATE call_billing
               SET summary_prompt_tokens     = :promptTokens,
                   summary_completion_tokens = :completionTokens,
                   summary_cached_tokens     = :cachedTokens,
                   llm_cost_uzs              = llm_cost_uzs + :costUzs,
                   total_uzs                 = total_uzs + :costUzs
             WHERE call_attempt_id = :callAttemptId
            """, nativeQuery = true)
    int addSummaryCharge(@Param("callAttemptId") long callAttemptId, @Param("promptTokens") long promptTokens,
                         @Param("completionTokens") long completionTokens, @Param("cachedTokens") long cachedTokens,
                         @Param("costUzs") long costUzs);

    /** An operator call's analysis — scorecard tokens and the operator side's recognition — added to its settled row (V24). */
    @Modifying @Transactional
    @Query(value = """
            UPDATE call_billing
               SET analysis_prompt_tokens     = :promptTokens,
                   analysis_completion_tokens = :completionTokens,
                   analysis_cached_tokens     = :cachedTokens,
                   analysis_stt_seconds       = :sttSeconds,
                   llm_cost_uzs               = llm_cost_uzs + :llmCostUzs,
                   stt_cost_uzs               = stt_cost_uzs + :sttCostUzs,
                   total_uzs                  = total_uzs + :totalCostUzs
             WHERE call_attempt_id = :callAttemptId
            """, nativeQuery = true)
    int addOperatorAnalysisCharge(@Param("callAttemptId") long callAttemptId, @Param("promptTokens") long promptTokens,
                                  @Param("completionTokens") long completionTokens,
                                  @Param("cachedTokens") long cachedTokens, @Param("sttSeconds") int sttSeconds,
                                  @Param("llmCostUzs") long llmCostUzs, @Param("sttCostUzs") long sttCostUzs,
                                  @Param("totalCostUzs") long totalCostUzs);
}
