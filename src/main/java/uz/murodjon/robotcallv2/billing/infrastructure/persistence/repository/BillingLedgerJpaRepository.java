package uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.murodjon.robotcallv2.billing.domain.enums.LedgerEntryType;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.BillingLedgerEntity;

import java.time.Instant;
import java.util.List;

public interface BillingLedgerJpaRepository extends JpaRepository<BillingLedgerEntity, Long> {

    boolean existsByCompanyIdAndIdempotencyKey(Long companyId, String idempotencyKey);

    @Query("""
            SELECT COALESCE(SUM(l.amountUzs), 0) FROM BillingLedgerEntity l
             WHERE l.companyId = :companyId AND l.entryType = :entryType AND l.createdAt >= :since
            """)
    long sumAmount(@Param("companyId") Long companyId, @Param("entryType") LedgerEntryType entryType,
                   @Param("since") Instant since);

    /*
     * "Extra" charges below: model tokens charged outside a call's billing row — every AI_CHARGE
     * (chat, copilot, questions about a call) and the CALL_CHARGE of a re-analysis, which is
     * taken off the balance but never added to the call's row. Amounts are signed debits, so
     * they are negated into spend.
     */

    /** Extra charges in {@code [from, to)} per source: reference_type, or 'reanalysis'. */
    @Query(value = """
            SELECT CASE WHEN entry_type = 'AI_CHARGE' THEN COALESCE(reference_type, 'other')
                        ELSE 'reanalysis' END        AS source,
                   COALESCE(SUM(-amount_uzs), 0)     AS spend_uzs,
                   COUNT(*)                          AS charges
              FROM billing_ledger
             WHERE company_id = :companyId
               AND created_at >= :from
               AND created_at < :to
               AND (entry_type = 'AI_CHARGE'
                    OR (entry_type = 'CALL_CHARGE' AND idempotency_key LIKE 'reanalysis-charge:%'))
             GROUP BY source
            """, nativeQuery = true)
    List<Object[]> findExtraCharges(@Param("companyId") long companyId, @Param("from") Instant from,
                                    @Param("to") Instant to);

    /** Extra charges in {@code [from, to)} per Tashkent calendar day, oldest first. */
    @Query(value = """
            SELECT CAST(created_at AT TIME ZONE 'Asia/Tashkent' AS date) AS day,
                   COALESCE(SUM(-amount_uzs), 0)                         AS spend_uzs
              FROM billing_ledger
             WHERE company_id = :companyId
               AND created_at >= :from
               AND created_at < :to
               AND (entry_type = 'AI_CHARGE'
                    OR (entry_type = 'CALL_CHARGE' AND idempotency_key LIKE 'reanalysis-charge:%'))
             GROUP BY day
             ORDER BY day
            """, nativeQuery = true)
    List<Object[]> findDailyExtraSpend(@Param("companyId") long companyId, @Param("from") Instant from,
                                       @Param("to") Instant to);

    /** Extra charges per Tashkent month ({@code yyyy-MM}), newest first, at most {@code limit} months. */
    @Query(value = """
            SELECT to_char(created_at AT TIME ZONE 'Asia/Tashkent', 'YYYY-MM') AS period,
                   COALESCE(SUM(-amount_uzs), 0)                               AS spend_uzs
              FROM billing_ledger
             WHERE company_id = :companyId
               AND (entry_type = 'AI_CHARGE'
                    OR (entry_type = 'CALL_CHARGE' AND idempotency_key LIKE 'reanalysis-charge:%'))
             GROUP BY period
             ORDER BY period DESC
             LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findMonthlyExtraSpend(@Param("companyId") long companyId, @Param("limit") int limit);
}
