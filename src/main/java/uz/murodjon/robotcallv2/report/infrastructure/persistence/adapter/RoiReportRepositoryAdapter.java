package uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.report.application.port.output.RoiReportRepository;
import uz.murodjon.robotcallv2.report.domain.entity.RoiTotals;

import java.time.Instant;

import static uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter.ReportSql.toTimestamptz;

/**
 * The ROI totals in one statement: the period's calls are picked once ({@code calls}) and
 * every figure — charges, promises, credited conversions — is joined to that same set, so
 * none of them can drift onto calls another figure did not count.
 */
@Component
public class RoiReportRepositoryAdapter implements RoiReportRepository {

    private static final String CALLS = """
            WITH calls AS (
                SELECT a.id, a.answered_at, a.duration_sec
                FROM call_attempt a
                LEFT JOIN campaign_target t ON t.id = a.target_id
                WHERE a.company_id = :companyId AND a.started_at >= :from AND a.started_at < :to
            """;

    private static final String TOTALS = """
            )
            SELECT
                (SELECT count(*) FROM calls) AS total_calls,
                (SELECT count(*) FROM calls WHERE answered_at IS NOT NULL) AS answered_calls,
                (SELECT coalesce(sum(duration_sec), 0) FROM calls WHERE answered_at IS NOT NULL) AS talk_seconds,
                (SELECT coalesce(sum(b.total_uzs), 0) FROM call_billing b JOIN calls c ON c.id = b.call_attempt_id
                  WHERE b.status = 'SETTLED') AS spent_uzs,
                (SELECT count(*) FROM payment_promise p JOIN calls c ON c.id = p.call_attempt_id) AS promises,
                (SELECT coalesce(sum(p.promised_amount), 0) FROM payment_promise p
                   JOIN calls c ON c.id = p.call_attempt_id) AS promised_amount,
                (SELECT count(*) FROM payment_promise p JOIN calls c ON c.id = p.call_attempt_id
                  WHERE p.status = 'KEPT') AS kept_promises,
                (SELECT coalesce(sum(p.promised_amount), 0) FROM payment_promise p
                   JOIN calls c ON c.id = p.call_attempt_id WHERE p.status = 'KEPT') AS kept_amount,
                (SELECT count(*) FROM conversion_attribution ca JOIN calls c ON c.id = ca.call_attempt_id) AS conversions,
                (SELECT coalesce(sum(ca.attributed_value_uzs), 0) FROM conversion_attribution ca
                   JOIN calls c ON c.id = ca.call_attempt_id) AS conversion_value
            """;

    private final JdbcClient jdbc;

    public RoiReportRepositoryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    @Override
    public RoiTotals findRoiTotals(long companyId, Instant from, Instant to, Long campaignId) {
        String campaignCondition = campaignId != null ? " AND t.campaign_id = :campaignId\n" : "";
        JdbcClient.StatementSpec statement = jdbc.sql(CALLS + campaignCondition + TOTALS)
                .param("companyId", companyId)
                .param("from", toTimestamptz(from))
                .param("to", toTimestamptz(to));
        if (campaignId != null) {
            statement = statement.param("campaignId", campaignId);
        }
        return statement.query((rs, n) -> new RoiTotals(
                        rs.getLong("total_calls"),
                        rs.getLong("answered_calls"),
                        rs.getLong("talk_seconds"),
                        rs.getLong("spent_uzs"),
                        rs.getLong("promises"),
                        rs.getLong("promised_amount"),
                        rs.getLong("kept_promises"),
                        rs.getLong("kept_amount"),
                        rs.getLong("conversions"),
                        rs.getLong("conversion_value")))
                .single();
    }
}
