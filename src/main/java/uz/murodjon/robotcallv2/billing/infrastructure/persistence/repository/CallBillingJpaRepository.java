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
                                + summary_prompt_tokens + summary_completion_tokens
                                + analysis_prompt_tokens + analysis_completion_tokens), 0) AS tokens,
                   COALESCE(SUM(tts_chars), 0)                           AS tts_chars,
                   COALESCE(SUM(total_uzs), 0)                           AS spend_uzs
              FROM call_billing
             WHERE company_id = :companyId
               AND status = 'SETTLED'
               AND settled_at >= :from
            """, nativeQuery = true)
    List<Object[]> findUsageSince(@Param("companyId") Long companyId, @Param("from") Instant from);

    /**
     * Usage and cost of the calls settled in {@code [from, to)}, as one row. Tokens are the
     * ones that were priced: a realtime call's own tokens are audio tokens billed through the
     * engine's minutes, so only its summary and analysis tokens count here. Input tokens are
     * the prompt tokens less the cached ones, which have their own column.
     */
    @Query(value = """
            SELECT COUNT(*)                                                                     AS calls,
                   COALESCE(SUM(duration_sec), 0)                                               AS duration_sec,
                   COALESCE(SUM(realtime_seconds), 0)                                           AS realtime_sec,
                   COALESCE(SUM(CASE WHEN realtime_seconds > 0 THEN 0 ELSE duration_sec END)
                            + SUM(analysis_stt_seconds), 0)                                     AS stt_sec,
                   COALESCE(SUM(GREATEST(
                            CASE WHEN realtime_seconds > 0 THEN 0 ELSE prompt_tokens - cached_tokens END
                            + summary_prompt_tokens - summary_cached_tokens
                            + analysis_prompt_tokens - analysis_cached_tokens, 0)), 0)          AS input_tokens,
                   COALESCE(SUM(CASE WHEN realtime_seconds > 0 THEN 0 ELSE completion_tokens END
                            + summary_completion_tokens + analysis_completion_tokens), 0)       AS output_tokens,
                   COALESCE(SUM(CASE WHEN realtime_seconds > 0 THEN 0 ELSE cached_tokens END
                            + summary_cached_tokens + analysis_cached_tokens), 0)               AS cached_tokens,
                   COALESCE(SUM(tts_chars), 0)                                                  AS tts_chars,
                   COALESCE(SUM(llm_prompt_cost_uzs), 0)                                        AS llm_prompt_uzs,
                   COALESCE(SUM(llm_completion_cost_uzs), 0)                                    AS llm_completion_uzs,
                   COALESCE(SUM(llm_cached_cost_uzs), 0)                                        AS llm_cached_uzs,
                   COALESCE(SUM(llm_cost_uzs), 0)                                               AS llm_uzs,
                   COALESCE(SUM(stt_cost_uzs), 0)                                               AS stt_uzs,
                   COALESCE(SUM(tts_cost_uzs), 0)                                               AS tts_uzs,
                   COALESCE(SUM(realtime_cost_uzs), 0)                                          AS realtime_uzs,
                   COALESCE(SUM(telephony_cost_uzs), 0)                                         AS telephony_uzs,
                   COALESCE(SUM(platform_fee_uzs), 0)                                           AS platform_fee_uzs,
                   COALESCE(SUM(total_uzs), 0)                                                  AS total_uzs
              FROM call_billing
             WHERE company_id = :companyId
               AND status = 'SETTLED'
               AND settled_at >= :from
               AND settled_at < :to
            """, nativeQuery = true)
    List<Object[]> findCostTotals(@Param("companyId") long companyId, @Param("from") Instant from,
                                  @Param("to") Instant to);

    /** What the calls settled in {@code [from, to)} cost, per Tashkent calendar day, oldest first. */
    @Query(value = """
            SELECT CAST(settled_at AT TIME ZONE 'Asia/Tashkent' AS date) AS day,
                   COALESCE(SUM(total_uzs), 0)                           AS spend_uzs
              FROM call_billing
             WHERE company_id = :companyId
               AND status = 'SETTLED'
               AND settled_at >= :from
               AND settled_at < :to
             GROUP BY day
             ORDER BY day
            """, nativeQuery = true)
    List<Object[]> findDailySpend(@Param("companyId") long companyId, @Param("from") Instant from,
                                  @Param("to") Instant to);

    /** The post-call summary, added to the call's settled row (V5); its price goes to the LLM line and its split (V41). */
    @Modifying @Transactional
    @Query(value = """
            UPDATE call_billing
               SET summary_prompt_tokens     = :promptTokens,
                   summary_completion_tokens = :completionTokens,
                   summary_cached_tokens     = :cachedTokens,
                   llm_cost_uzs              = llm_cost_uzs + :llmCostUzs,
                   llm_prompt_cost_uzs       = llm_prompt_cost_uzs + :llmPromptCostUzs,
                   llm_completion_cost_uzs   = llm_completion_cost_uzs + :llmCompletionCostUzs,
                   llm_cached_cost_uzs       = llm_cached_cost_uzs + :llmCachedCostUzs,
                   total_uzs                 = total_uzs + :totalCostUzs
             WHERE call_attempt_id = :callAttemptId
            """, nativeQuery = true)
    int addSummaryCharge(@Param("callAttemptId") long callAttemptId, @Param("promptTokens") long promptTokens,
                         @Param("completionTokens") long completionTokens, @Param("cachedTokens") long cachedTokens,
                         @Param("llmCostUzs") long llmCostUzs, @Param("llmPromptCostUzs") long llmPromptCostUzs,
                         @Param("llmCompletionCostUzs") long llmCompletionCostUzs,
                         @Param("llmCachedCostUzs") long llmCachedCostUzs, @Param("totalCostUzs") long totalCostUzs);

    /** An operator call's analysis — scorecard tokens and the operator side's recognition — added to its settled row (V24). */
    @Modifying @Transactional
    @Query(value = """
            UPDATE call_billing
               SET analysis_prompt_tokens     = :promptTokens,
                   analysis_completion_tokens = :completionTokens,
                   analysis_cached_tokens     = :cachedTokens,
                   analysis_stt_seconds       = :sttSeconds,
                   llm_cost_uzs               = llm_cost_uzs + :llmCostUzs,
                   llm_prompt_cost_uzs        = llm_prompt_cost_uzs + :llmPromptCostUzs,
                   llm_completion_cost_uzs    = llm_completion_cost_uzs + :llmCompletionCostUzs,
                   llm_cached_cost_uzs        = llm_cached_cost_uzs + :llmCachedCostUzs,
                   stt_cost_uzs               = stt_cost_uzs + :sttCostUzs,
                   total_uzs                  = total_uzs + :totalCostUzs
             WHERE call_attempt_id = :callAttemptId
            """, nativeQuery = true)
    int addOperatorAnalysisCharge(@Param("callAttemptId") long callAttemptId, @Param("promptTokens") long promptTokens,
                                  @Param("completionTokens") long completionTokens,
                                  @Param("cachedTokens") long cachedTokens, @Param("sttSeconds") int sttSeconds,
                                  @Param("llmCostUzs") long llmCostUzs, @Param("llmPromptCostUzs") long llmPromptCostUzs,
                                  @Param("llmCompletionCostUzs") long llmCompletionCostUzs,
                                  @Param("llmCachedCostUzs") long llmCachedCostUzs,
                                  @Param("sttCostUzs") long sttCostUzs, @Param("totalCostUzs") long totalCostUzs);
}
