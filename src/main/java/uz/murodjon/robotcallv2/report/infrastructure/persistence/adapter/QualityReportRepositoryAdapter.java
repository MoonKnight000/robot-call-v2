package uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.report.application.port.output.QualityReportRepository;
import uz.murodjon.robotcallv2.report.domain.entity.CriterionTrendPoint;
import uz.murodjon.robotcallv2.report.domain.entity.FailedCriterion;
import uz.murodjon.robotcallv2.report.domain.entity.OperatorQualityScore;
import uz.murodjon.robotcallv2.report.domain.entity.TopicCount;
import uz.murodjon.robotcallv2.report.domain.enums.TrendBucket;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import static uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter.ReportSql.*;

/**
 * Operator call quality over {@code call_scorecard}, its answers and topics, and
 * {@code call_talk_metrics}. A scorecard falls into a period by when it was made — minutes after
 * its call ended.
 */
@Component
public class QualityReportRepositoryAdapter implements QualityReportRepository {

    /** A criterion is one by its id; a deleted one (id gone) by the name its calls kept. */
    private static final String CRITERION_KEY =
            "a.criterion_id, CASE WHEN a.criterion_id IS NULL THEN a.criterion_name END";
    private static final String UPSET = "s.sentiment IN ('NEGATIVE', 'HOSTILE')";

    private final JdbcClient jdbc;

    public QualityReportRepositoryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    @Override
    public List<OperatorQualityScore> findOperatorQualityScores(long companyId, Instant from, Instant to) {
        return jdbc.sql("SELECT u.id AS user_id, u.name AS user_name, count(s.call_id) AS scored, "
                        + "avg(s.score) AS avg_score, "
                        + "count(s.call_id) FILTER (WHERE s.needs_review) AS flagged, "
                        + "count(s.call_id) FILTER (WHERE s.review_status = 'CORRECTED') AS corrected, "
                        + "count(s.call_id) FILTER (WHERE " + UPSET + ") AS upset, "
                        + "avg(m.operator_talk_ms * 100.0 / nullif(m.client_talk_ms + m.operator_talk_ms, 0)) "
                        + "  AS avg_talk_share, "
                        + "avg(m.overtalk_ms * 100.0 / nullif(m.analysed_ms, 0)) AS avg_overtalk, "
                        + "avg(m.longest_silence_ms) / 1000.0 AS avg_longest_silence "
                        + "FROM operator_sip_account o JOIN app_user u ON u.id = o.user_id "
                        + "LEFT JOIN call_scorecard s ON s.operator_user_id = u.id AND s.company_id = o.company_id "
                        + "  AND s.created_at >= :from AND s.created_at < :to "
                        + "LEFT JOIN call_talk_metrics m ON m.call_id = s.call_id "
                        + "WHERE o.company_id = :companyId "
                        + "GROUP BY u.id, u.name ORDER BY avg(s.score) DESC NULLS LAST, u.name")
                .param("companyId", companyId)
                .param("from", toTimestamptz(from))
                .param("to", toTimestamptz(to))
                .query((rs, n) -> new OperatorQualityScore(rs.getLong("user_id"), rs.getString("user_name"),
                        rs.getLong("scored"), doubleOrNull(rs, "avg_score"), rs.getLong("flagged"),
                        rs.getLong("corrected"), rs.getLong("upset"), doubleOrNull(rs, "avg_talk_share"),
                        doubleOrNull(rs, "avg_overtalk"), doubleOrNull(rs, "avg_longest_silence")))
                .list();
    }

    @Override
    public List<CriterionTrendPoint> findCriterionTrend(long companyId, Instant from, Instant to, ZoneId zone,
                                                        TrendBucket bucket, Long operatorUserId) {
        return scorecards("SELECT a.criterion_id AS criterion_id, "
                        + "(array_agg(a.criterion_name ORDER BY s.created_at DESC))[1] AS criterion_name, "
                        + "CAST(date_trunc(:bucket, s.created_at AT TIME ZONE :zone) AS date) AS period_start, "
                        + "count(*) FILTER (WHERE a.answer <> 'NOT_APPLICABLE') AS applied, "
                        + "count(*) FILTER (WHERE a.answer = 'YES') AS passed "
                        + "FROM call_scorecard s JOIN call_scorecard_answer a ON a.call_id = s.call_id",
                companyId, from, to, operatorUserId)
                .bind("bucket", bucket.name().toLowerCase(Locale.ROOT))
                .bind("zone", zone.getId())
                .append("GROUP BY " + CRITERION_KEY + ", period_start ORDER BY criterion_name, period_start")
                .prepare(jdbc)
                .query((rs, n) -> {
                    long applied = rs.getLong("applied");
                    long passed = rs.getLong("passed");
                    return new CriterionTrendPoint(longOrNull(rs, "criterion_id"), rs.getString("criterion_name"),
                            localDateOrNull(rs, "period_start"), applied, passed,
                            applied > 0 ? passed * 100.0 / applied : null);
                })
                .list();
    }

    @Override
    public List<FailedCriterion> findFailedCriteria(long companyId, Instant from, Instant to, Long operatorUserId) {
        return scorecards("SELECT a.criterion_id AS criterion_id, "
                        + "(array_agg(a.criterion_name ORDER BY s.created_at DESC))[1] AS criterion_name, "
                        + "count(*) FILTER (WHERE a.answer <> 'NOT_APPLICABLE') AS applied, "
                        + "count(*) FILTER (WHERE a.answer = 'NO') AS failed "
                        + "FROM call_scorecard s JOIN call_scorecard_answer a ON a.call_id = s.call_id",
                companyId, from, to, operatorUserId)
                .append("GROUP BY " + CRITERION_KEY
                        + " HAVING count(*) FILTER (WHERE a.answer <> 'NOT_APPLICABLE') > 0"
                        + " ORDER BY failed DESC, criterion_name")
                .prepare(jdbc)
                .query((rs, n) -> {
                    long applied = rs.getLong("applied");
                    long failed = rs.getLong("failed");
                    return new FailedCriterion(longOrNull(rs, "criterion_id"), rs.getString("criterion_name"),
                            applied, failed, failed * 100.0 / applied);
                })
                .list();
    }

    @Override
    public List<TopicCount> findTopicCounts(long companyId, Instant from, Instant to, Long operatorUserId,
                                            int limit) {
        return scorecards("SELECT t.topic AS topic, count(*) AS calls, avg(s.score) AS avg_score, "
                        + "count(*) FILTER (WHERE " + UPSET + ") AS upset "
                        + "FROM call_scorecard s JOIN call_scorecard_topic t ON t.call_id = s.call_id",
                companyId, from, to, operatorUserId)
                .append("GROUP BY t.topic ORDER BY calls DESC, t.topic LIMIT :limit")
                .bind("limit", limit)
                .prepare(jdbc)
                .query((rs, n) -> new TopicCount(rs.getString("topic"), rs.getLong("calls"),
                        doubleOrNull(rs, "avg_score"), rs.getLong("upset")))
                .list();
    }

    /** The company's scorecards made in [from, to), one operator's when {@code operatorUserId} is set. */
    private static SqlStatement scorecards(String head, long companyId, Instant from, Instant to, Long operatorUserId) {
        SqlStatement statement = new SqlStatement(head)
                .where("s.company_id = :companyId", "companyId", companyId)
                .where("s.created_at >= :from", "from", toTimestamptz(from))
                .where("s.created_at < :to", "to", toTimestamptz(to));
        if (operatorUserId != null) {
            statement.where("s.operator_user_id = :operatorUserId", "operatorUserId", operatorUserId);
        }
        return statement;
    }
}
