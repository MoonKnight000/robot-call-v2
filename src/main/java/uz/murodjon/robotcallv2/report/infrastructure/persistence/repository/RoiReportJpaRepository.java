package uz.murodjon.robotcallv2.report.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import uz.murodjon.robotcallv2.report.infrastructure.persistence.entity.ReportScheduleEntity;

import java.time.Instant;

/**
 * The ROI totals in one statement: the period's calls are picked once ({@code calls}) and
 * every figure — charges, promises, credited conversions — is joined to that same set, so
 * none of them can drift onto calls another figure did not count.
 */
public interface RoiReportJpaRepository extends Repository<ReportScheduleEntity, Long> {

    @Query(value = """
            WITH calls AS (
                SELECT a.id, a.answered_at, a.duration_sec
                FROM call_attempt a
                LEFT JOIN campaign_target t ON t.id = a.target_id
                WHERE a.company_id = :companyId AND a.started_at >= :from AND a.started_at < :to
                  AND (CAST(:campaignId AS bigint) IS NULL OR t.campaign_id = CAST(:campaignId AS bigint))
            )
            SELECT
                (SELECT count(*) FROM calls) AS "totalCalls",
                (SELECT count(*) FROM calls WHERE answered_at IS NOT NULL) AS "answeredCalls",
                (SELECT coalesce(sum(duration_sec), 0) FROM calls WHERE answered_at IS NOT NULL) AS "talkSeconds",
                (SELECT coalesce(sum(b.total_uzs), 0) FROM call_billing b JOIN calls c ON c.id = b.call_attempt_id
                  WHERE b.status = 'SETTLED') AS "spentUzs",
                (SELECT count(*) FROM call_result r JOIN calls c ON c.id = r.call_id
                  WHERE r.promised_date IS NOT NULL) AS promises,
                (SELECT coalesce(sum(r.promised_amount), 0) FROM call_result r JOIN calls c ON c.id = r.call_id
                  WHERE r.promised_date IS NOT NULL) AS "promisedAmount",
                (SELECT count(*) FROM conversion_attribution ca JOIN calls c ON c.id = ca.call_attempt_id) AS conversions,
                (SELECT coalesce(sum(ca.attributed_value_uzs), 0) FROM conversion_attribution ca
                   JOIN calls c ON c.id = ca.call_attempt_id) AS "conversionValue"
            """, nativeQuery = true)
    RoiTotalsProjection findRoiTotals(@Param("companyId") long companyId, @Param("from") Instant from,
                                      @Param("to") Instant to, @Param("campaignId") Long campaignId);
}
