package uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.report.application.port.output.DashboardReportRepository;
import uz.murodjon.robotcallv2.report.domain.entity.*;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

import static uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter.ReportSql.*;

/**
 * Period analytics. Native because it is analytical — FILTER aggregates, date_trunc
 * buckets — and run through {@link JdbcClient}, every column read by its alias.
 *
 * <p>Every figure that takes a {@link DashboardFilter} starts from {@link #buildDashboardQuery}:
 * one join chain, one WHERE, and dispositions bound from {@link Disposition} rather than
 * spelled in SQL — so the KPI cards, the charts and the summary page count the same calls,
 * the same success, the same failures ({@link #FAILED_DISPOSITIONS}) and the same average
 * duration ({@link #AVG_ANSWERED_DURATION}).
 */
@Component
public class DashboardReportRepositoryAdapter implements DashboardReportRepository {

    /** A successful call — the one definition every figure here uses ({@link Disposition#isConversion()}). */
    static final List<String> SUCCESS_DISPOSITIONS = Arrays.stream(Disposition.values())
            .filter(Disposition::isConversion)
            .map(Enum::name)
            .toList();

    /** A call the network or the carrier failed — every "failed" figure: the charts' error series, the summary's failed calls. */
    static final List<String> FAILED_DISPOSITIONS = names(Disposition.FAILED, Disposition.CARRIER_REJECTED);

    /**
     * Average call length, over answered calls only ({@code answered_at} set) — the one
     * definition every figure here uses. No answered call gives SQL null: a nullable figure
     * reports null, a primitive one 0.
     */
    static final String AVG_ANSWERED_DURATION = "avg(a.duration_sec) FILTER (WHERE a.answered_at IS NOT NULL)";

    /** The funnel's last stage: the conversation reached an outcome, good or bad. */
    static final List<String> RESULT_DISPOSITIONS = names(Disposition.PROMISE_TO_PAY, Disposition.REFUSED,
            Disposition.TRANSFERRED, Disposition.COMPLETED, Disposition.DO_NOT_CALL,
            Disposition.GOAL_REACHED, Disposition.GOAL_NOT_REACHED);

    /**
     * The join chain every dashboard figure is measured over.
     *
     * <p>A call reaches its agent one of two ways — through the campaign that dialled it,
     * or through the inbound route that answered it — so the agent is joined twice and
     * every join is outer: a widget call or a manual one has neither, and an inner join
     * would quietly drop it from every total on the page. Each join is on a primary key, so
     * none multiplies a call, and the planner drops the ones a figure does not read.
     */
    private static final String DASHBOARD_FROM = """
            FROM call_attempt a
            LEFT JOIN campaign_target t ON t.id = a.target_id
            LEFT JOIN campaign c ON c.id = t.campaign_id
            LEFT JOIN ai_agent ag ON ag.id = c.ai_agent_id
            LEFT JOIN inbound_route ir ON ir.id = a.inbound_route_id
            LEFT JOIN ai_agent ir_ag ON ir_ag.id = ir.ai_agent_id
            """;

    /** Added by the two figures that name whoever took the call, agent or human. */
    private static final String OPERATOR_JOIN = "LEFT JOIN app_user u ON u.id = a.operator_user_id\n";

    /** Added by the dynamics chart, which can narrow to escalated calls. */
    private static final String RESULT_JOIN = "LEFT JOIN call_result r ON r.call_id = a.id\n";

    /** Added by the funnel, whose conversation stage needs the turn count. */
    private static final String TECHNICAL_JOIN = "LEFT JOIN call_technical ct ON ct.call_id = a.id\n";

    private static final String TOTALS_SELECT = """
            SELECT count(*) AS total_calls,
                   count(*) FILTER (WHERE a.answered_at IS NOT NULL) AS answered_calls,
                   %s AS avg_duration_sec,
                   count(*) FILTER (WHERE a.disposition = :promiseToPay) AS promises
            """.formatted(AVG_ANSWERED_DURATION);

    private static final String BUCKET_SELECT = """
            SELECT date_trunc(:granularity, a.started_at, :zone) AS bucket,
                   count(*) AS total,
                   count(*) FILTER (WHERE a.answered_at IS NOT NULL) AS answered,
                   count(*) FILTER (WHERE a.disposition = :noAnswer) AS no_answer,
                   count(*) FILTER (WHERE a.disposition IN (:failedDispositions)) AS error_count,
                   %s AS avg_duration_sec,
                   count(*) FILTER (WHERE a.disposition = :promiseToPay) AS promises
            """.formatted(AVG_ANSWERED_DURATION);

    private static final int[] DURATION_BUCKET_BOUNDARIES = {30, 60, 120, 300, 600};

    private final JdbcClient jdbc;

    public DashboardReportRepositoryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    /**
     * The start every filtered figure shares: {@code head} (a SELECT over
     * {@link #DASHBOARD_FROM}, plus any extra join) bounded by the company and the period and
     * narrowed by the filter. The scenario is accepted from either agent — the campaign's or
     * the inbound route's — because a scenario is spoken on calls that arrive both ways, and
     * matching only the outbound side would silently halve an inbound-heavy company's numbers.
     */
    static SqlStatement buildDashboardQuery(String head, long companyId, DashboardFilter filter) {
        SqlStatement statement = new SqlStatement(head)
                .where("a.company_id = :companyId", "companyId", companyId)
                .where("a.started_at >= :from", "from", toTimestamptz(filter.from()))
                .where("a.started_at < :to", "to", toTimestamptz(filter.to()));
        if (filter.campaignId() != null) {
            statement.where("t.campaign_id = :campaignId", "campaignId", filter.campaignId());
        }
        if (filter.scenarioId() != null) {
            statement.where("(ag.scenario_id = :scenarioId OR ir_ag.scenario_id = :scenarioId)",
                    "scenarioId", filter.scenarioId());
        }
        return statement;
    }

    @Override
    public DashboardTotals findDashboardTotals(long companyId, DashboardFilter filter) {
        return buildDashboardQuery(TOTALS_SELECT + DASHBOARD_FROM, companyId, filter)
                .bind("promiseToPay", Disposition.PROMISE_TO_PAY.name())
                .prepare(jdbc)
                .query(DashboardReportRepositoryAdapter::toDashboardTotals)
                .single();
    }

    @Override
    public DashboardTotals findOperatorTotals(long companyId, Instant from, Instant to, long operatorUserId) {
        return new SqlStatement(TOTALS_SELECT + "FROM call_attempt a\n")
                .where("a.company_id = :companyId", "companyId", companyId)
                .where("a.operator_user_id = :operatorUserId", "operatorUserId", operatorUserId)
                .where("a.started_at >= :from", "from", toTimestamptz(from))
                .where("a.started_at < :to", "to", toTimestamptz(to))
                .bind("promiseToPay", Disposition.PROMISE_TO_PAY.name())
                .prepare(jdbc)
                .query(DashboardReportRepositoryAdapter::toDashboardTotals)
                .single();
    }

    private static DashboardTotals toDashboardTotals(ResultSet rs, int rowNum) throws SQLException {
        return new DashboardTotals(rs.getLong("total_calls"), rs.getLong("answered_calls"),
                doubleOrNull(rs, "avg_duration_sec"), rs.getLong("promises"));
    }

    @Override
    public List<DashboardBucket> findDashboardBuckets(long companyId, DashboardFilter filter, String granularity) {
        return bindBucketParams(buildDashboardQuery(BUCKET_SELECT + DASHBOARD_FROM, companyId, filter), granularity,
                filter.zone())
                .append("GROUP BY bucket ORDER BY bucket")
                .prepare(jdbc)
                .query(DashboardReportRepositoryAdapter::toDashboardBucket)
                .list();
    }

    @Override
    public List<DashboardBucket> findDynamicsBuckets(long companyId, DashboardFilter filter, Boolean escalated,
                                                     String granularity) {
        SqlStatement statement = buildDashboardQuery(BUCKET_SELECT + DASHBOARD_FROM + RESULT_JOIN, companyId, filter);
        if (escalated != null) {
            statement.where("coalesce(r.escalated, false) = :escalated", "escalated", escalated);
        }
        return bindBucketParams(statement, granularity, filter.zone())
                .append("GROUP BY bucket ORDER BY bucket")
                .prepare(jdbc)
                .query(DashboardReportRepositoryAdapter::toDashboardBucket)
                .list();
    }

    static SqlStatement bindBucketParams(SqlStatement statement, String granularity, ZoneId zone) {
        return statement.bind("granularity", granularity)
                .bind("zone", zone.getId())
                .bind("noAnswer", Disposition.NO_ANSWER.name())
                .bind("failedDispositions", FAILED_DISPOSITIONS)
                .bind("promiseToPay", Disposition.PROMISE_TO_PAY.name());
    }

    private static DashboardBucket toDashboardBucket(ResultSet rs, int rowNum) throws SQLException {
        return new DashboardBucket(instantOrNull(rs, "bucket"), rs.getLong("total"), rs.getLong("answered"),
                rs.getLong("no_answer"), rs.getLong("error_count"), doubleOrNull(rs, "avg_duration_sec"),
                rs.getLong("promises"));
    }

    @Override
    public List<DashboardOutcome> findDashboardOutcomes(long companyId, DashboardFilter filter) {
        return buildDashboardQuery(
                        "SELECT coalesce(a.disposition, 'UNKNOWN') AS disposition, count(*) AS n\n" + DASHBOARD_FROM,
                        companyId, filter)
                .append("GROUP BY coalesce(a.disposition, 'UNKNOWN') ORDER BY n DESC")
                .prepare(jdbc)
                .query((rs, n) -> new DashboardOutcome(rs.getString("disposition"), rs.getLong("n")))
                .list();
    }

    @Override
    public List<HourlyHeatmapCell> findHourlyHeatmap(long companyId, DashboardFilter filter) {
        return buildDashboardQuery("""
                        SELECT extract(dow from a.started_at AT TIME ZONE :zone)::int AS day_of_week,
                               extract(hour from a.started_at AT TIME ZONE :zone)::int AS hour_of_day,
                               count(*) AS total,
                               count(*) FILTER (WHERE a.answered_at IS NOT NULL) AS answered
                        """ + DASHBOARD_FROM, companyId, filter)
                .bind("zone", filter.zone().getId())
                .append("GROUP BY day_of_week, hour_of_day ORDER BY day_of_week, hour_of_day")
                .prepare(jdbc)
                .query((rs, n) -> {
                    long total = rs.getLong("total");
                    long answered = rs.getLong("answered");
                    return new HourlyHeatmapCell(rs.getInt("day_of_week"), rs.getInt("hour_of_day"), total, answered,
                            rate(answered, total));
                })
                .list();
    }

    @Override
    public List<CampaignComparison> findCampaignComparison(long companyId, Instant from, Instant to,
                                                           List<Long> campaignIds) {
        SqlStatement statement = new SqlStatement("""
                SELECT c.id AS campaign_id, c.name AS campaign_name,
                       count(*) AS total_calls,
                       count(*) FILTER (WHERE a.answered_at IS NOT NULL) AS answered_calls,
                       %s AS avg_duration_sec,
                       count(*) FILTER (WHERE a.disposition = :promiseToPay) AS promises
                FROM call_attempt a
                JOIN campaign_target t ON t.id = a.target_id
                JOIN campaign c ON c.id = t.campaign_id
                """.formatted(AVG_ANSWERED_DURATION))
                .where("a.company_id = :companyId", "companyId", companyId)
                .where("a.started_at >= :from", "from", toTimestamptz(from))
                .where("a.started_at < :to", "to", toTimestamptz(to))
                .bind("promiseToPay", Disposition.PROMISE_TO_PAY.name());
        if (campaignIds != null && !campaignIds.isEmpty()) {
            statement.where("c.id IN (:campaignIds)", "campaignIds", campaignIds);
        }
        return statement.append("GROUP BY c.id, c.name ORDER BY total_calls DESC")
                .prepare(jdbc)
                .query((rs, n) -> {
                    long total = rs.getLong("total_calls");
                    long answered = rs.getLong("answered_calls");
                    return new CampaignComparison(
                            rs.getLong("campaign_id"), rs.getString("campaign_name"), total, answered,
                            rate(answered, total), doubleOrNull(rs, "avg_duration_sec"), rs.getLong("promises"));
                })
                .list();
    }

    @Override
    public List<DurationHistogramBucket> findDurationHistogram(long companyId, DashboardFilter filter) {
        Map<String, Long> counts = new LinkedHashMap<>();
        buildDashboardQuery("""
                        SELECT CASE
                               WHEN a.duration_sec < 30 THEN '0-30'
                               WHEN a.duration_sec < 60 THEN '30-60'
                               WHEN a.duration_sec < 120 THEN '60-120'
                               WHEN a.duration_sec < 300 THEN '120-300'
                               WHEN a.duration_sec < 600 THEN '300-600'
                               ELSE '600+' END AS bucket_label,
                               count(*) AS n
                        """ + DASHBOARD_FROM, companyId, filter)
                .where("a.duration_sec IS NOT NULL")
                .append("GROUP BY bucket_label")
                .prepare(jdbc)
                .query((ResultSet rs) -> {
                    counts.put(rs.getString("bucket_label"), rs.getLong("n"));
                });
        List<DurationHistogramBucket> buckets = new ArrayList<>();
        int start = 0;
        for (int boundary : DURATION_BUCKET_BOUNDARIES) {
            String label = start + "-" + boundary;
            buckets.add(new DurationHistogramBucket(label, start, boundary, counts.getOrDefault(label, 0L)));
            start = boundary;
        }
        buckets.add(new DurationHistogramBucket(start + "+", start, null, counts.getOrDefault(start + "+", 0L)));
        return buckets;
    }

    @Override
    public List<FunnelStage> findFunnel(long companyId, DashboardFilter filter) {
        return buildDashboardQuery("""
                        SELECT count(*) AS calls,
                               count(*) FILTER (WHERE a.answered_at IS NOT NULL) AS answered,
                               count(*) FILTER (WHERE a.answered_at IS NOT NULL
                                   AND (a.disposition IS NULL OR a.disposition <> :wrongNumber)) AS person_confirmed,
                               count(*) FILTER (WHERE a.answered_at IS NOT NULL
                                   AND (a.disposition IS NULL OR a.disposition <> :wrongNumber)
                                   AND coalesce(ct.turn_count, 0) > 0) AS conversation,
                               count(*) FILTER (WHERE a.disposition IN (:resultDispositions)) AS result
                        """ + DASHBOARD_FROM + TECHNICAL_JOIN, companyId, filter)
                .bind("wrongNumber", Disposition.WRONG_NUMBER.name())
                .bind("resultDispositions", RESULT_DISPOSITIONS)
                .prepare(jdbc)
                .query((rs, n) -> {
                    long calls = rs.getLong("calls");
                    long answered = rs.getLong("answered");
                    long personConfirmed = rs.getLong("person_confirmed");
                    long conversation = rs.getLong("conversation");
                    long result = rs.getLong("result");
                    return List.of(
                            new FunnelStage("CALL", calls, rate(calls, calls)),
                            new FunnelStage("ANSWERED", answered, rate(answered, calls)),
                            new FunnelStage("PERSON_CONFIRMED", personConfirmed, rate(personConfirmed, calls)),
                            new FunnelStage("CONVERSATION", conversation, rate(conversation, calls)),
                            new FunnelStage("RESULT", result, rate(result, calls)));
                })
                .single();
    }

    @Override
    public DashboardAggregates findDashboardAggregates(long companyId, DashboardFilter filter) {
        return buildDashboardQuery("""
                        SELECT
                            COUNT(a.id) AS total_calls,
                            COALESCE(SUM(a.duration_sec), 0) / 60 AS total_minutes,
                            COALESCE(%s, 0) AS avg_duration_sec,
                            COUNT(a.id) FILTER (WHERE a.disposition IN (:successDispositions)) AS completed_calls,
                            COUNT(a.id) FILTER (WHERE a.disposition IN (:failedDispositions)) AS failed_calls,
                            COUNT(a.id) FILTER (WHERE a.disposition = :noAnswer) AS missed_calls
                        """.formatted(AVG_ANSWERED_DURATION) + DASHBOARD_FROM, companyId, filter)
                .bind("successDispositions", SUCCESS_DISPOSITIONS)
                .bind("failedDispositions", FAILED_DISPOSITIONS)
                .bind("noAnswer", Disposition.NO_ANSWER.name())
                .prepare(jdbc)
                .query((rs, n) -> new DashboardAggregates(
                        rs.getLong("total_calls"),
                        rs.getLong("total_minutes"),
                        rs.getDouble("avg_duration_sec"),
                        rs.getLong("completed_calls"),
                        rs.getLong("failed_calls"),
                        rs.getLong("missed_calls")))
                .single();
    }

    @Override
    public List<DashboardTimelineBucket> findDashboardTimelineBuckets(long companyId, DashboardFilter filter,
                                                                      String granularity) {
        return buildDashboardQuery("""
                        SELECT
                            date_trunc(:granularity, a.started_at, :zone) AS bucket,
                            COUNT(a.id) AS calls,
                            COALESCE(SUM(a.duration_sec), 0) / 60 AS minutes,
                            COUNT(a.id) FILTER (WHERE a.disposition IN (:failedDispositions)) AS failed,
                            COUNT(a.id) FILTER (WHERE a.disposition IN (:successDispositions)) AS completed,
                            COUNT(a.id) FILTER (WHERE a.disposition = :noAnswer) AS missed,
                            COALESCE(%s, 0) AS avg_duration_sec
                        """.formatted(AVG_ANSWERED_DURATION) + DASHBOARD_FROM, companyId, filter)
                .bind("granularity", granularity)
                .bind("zone", filter.zone().getId())
                .bind("successDispositions", SUCCESS_DISPOSITIONS)
                .bind("failedDispositions", FAILED_DISPOSITIONS)
                .bind("noAnswer", Disposition.NO_ANSWER.name())
                .append("GROUP BY bucket ORDER BY bucket")
                .prepare(jdbc)
                .query((rs, n) -> new DashboardTimelineBucket(
                        instantOrNull(rs, "bucket"),
                        rs.getLong("calls"),
                        rs.getLong("minutes"),
                        rs.getLong("failed"),
                        rs.getLong("completed"),
                        rs.getLong("missed"),
                        rs.getDouble("avg_duration_sec")))
                .list();
    }

    @Override
    public List<DashboardDirectionTotals> findDashboardDirectionTotals(long companyId, DashboardFilter filter) {
        return buildDashboardQuery("""
                        SELECT
                            CASE WHEN a.inbound_route_id IS NOT NULL THEN 'inbound' ELSE 'outbound' END AS direction,
                            COUNT(a.id) AS total_calls,
                            COALESCE(SUM(a.duration_sec), 0) / 60 AS total_minutes,
                            COALESCE(%s, 0) AS avg_duration_sec,
                            COUNT(a.id) FILTER (WHERE a.disposition IN (:successDispositions)) AS completed_calls
                        """.formatted(AVG_ANSWERED_DURATION) + DASHBOARD_FROM, companyId, filter)
                .bind("successDispositions", SUCCESS_DISPOSITIONS)
                .append("GROUP BY (CASE WHEN a.inbound_route_id IS NOT NULL THEN 'inbound' ELSE 'outbound' END)")
                .prepare(jdbc)
                .query((rs, n) -> new DashboardDirectionTotals(
                        rs.getString("direction"),
                        rs.getLong("total_calls"),
                        rs.getLong("total_minutes"),
                        // The average is numeric; truncated to whole seconds as before.
                        (int) rs.getDouble("avg_duration_sec"),
                        rs.getLong("completed_calls")))
                .list();
    }

    @Override
    public List<DashboardAgentTotals> findDashboardTopAgents(long companyId, DashboardFilter filter, int limit) {
        return buildDashboardQuery("""
                        SELECT
                            COALESCE(u.id, ag.id, ir_ag.id, 0) AS agent_id,
                            COALESCE(u.name, ag.name, ir_ag.name, 'AI Agent') AS agent_name,
                            CASE WHEN u.id IS NOT NULL THEN 'operator' ELSE 'ai' END AS agent_type,
                            COUNT(a.id) AS calls_count,
                            COALESCE(SUM(a.duration_sec), 0) / 60 AS total_minutes,
                            ROUND(COUNT(a.id) FILTER (WHERE a.disposition IN (:successDispositions))::numeric * 100 / NULLIF(COUNT(a.id), 0), 1) AS success_rate
                        """ + DASHBOARD_FROM + OPERATOR_JOIN, companyId, filter)
                .bind("successDispositions", SUCCESS_DISPOSITIONS)
                .append("GROUP BY agent_id, agent_name, agent_type ORDER BY calls_count DESC LIMIT :limit")
                .bind("limit", limit)
                .prepare(jdbc)
                .query((rs, n) -> new DashboardAgentTotals(
                        rs.getLong("agent_id"),
                        rs.getString("agent_name"),
                        rs.getString("agent_type"),
                        rs.getLong("calls_count"),
                        rs.getLong("total_minutes"),
                        rs.getDouble("success_rate")))
                .list();
    }

    @Override
    public List<DashboardCampaignProgress> findDashboardActiveCampaigns(long companyId, int limit) {
        return jdbc.sql("""
                        SELECT c.id AS campaign_id, c.name AS campaign_name,
                               COUNT(t.id) AS total_targets,
                               COUNT(t.id) FILTER (WHERE t.status IN ('DONE', 'FAILED', 'EXHAUSTED')) AS done_targets
                        FROM campaign c
                        LEFT JOIN campaign_target t ON t.campaign_id = c.id AND t.phone NOT IN ('MANUAL', 'INBOUND')
                        WHERE c.company_id = :companyId
                          AND c.name NOT IN ('MANUAL', 'INBOUND')
                        GROUP BY c.id, c.name
                        ORDER BY c.id DESC
                        LIMIT :limit
                        """)
                .param("companyId", companyId)
                .param("limit", limit)
                .query((rs, n) -> new DashboardCampaignProgress(
                        rs.getLong("campaign_id"),
                        rs.getString("campaign_name"),
                        rs.getLong("total_targets"),
                        rs.getLong("done_targets")))
                .list();
    }

    @Override
    public List<DashboardLiveCall> findDashboardLiveCalls(long companyId, int limit) {
        return jdbc.sql("""
                        SELECT a.id AS call_id,
                               COALESCE(a.phone, t.phone) AS phone,
                               t.context_data ->> 'clientName' AS client_name,
                               c.name AS campaign_name,
                               a.started_at AS started_at,
                               CASE WHEN a.inbound_route_id IS NOT NULL THEN 'inbound' ELSE 'outbound' END AS direction
                        FROM call_attempt a
                        LEFT JOIN campaign_target t ON t.id = a.target_id
                        LEFT JOIN campaign c ON c.id = t.campaign_id
                        WHERE a.company_id = :companyId
                          AND a.started_at IS NOT NULL
                          AND a.ended_at IS NULL
                        ORDER BY a.started_at DESC
                        LIMIT :limit
                        """)
                .param("companyId", companyId)
                .param("limit", limit)
                .query((rs, n) -> new DashboardLiveCall(
                        rs.getLong("call_id"),
                        rs.getString("phone"),
                        rs.getString("client_name"),
                        rs.getString("campaign_name"),
                        instantOrNull(rs, "started_at"),
                        rs.getString("direction")))
                .list();
    }

    @Override
    public List<DashboardRecentCall> findDashboardRecentCalls(long companyId, DashboardFilter filter, int limit) {
        return buildDashboardQuery("""
                        SELECT a.id AS call_id,
                               COALESCE(a.phone, t.phone) AS phone,
                               t.context_data ->> 'clientName' AS client_name,
                               c.name AS campaign_name,
                               u.name AS operator_name,
                               CASE WHEN a.inbound_route_id IS NOT NULL THEN 'inbound' ELSE 'outbound' END AS direction,
                               COALESCE(a.disposition, 'UNKNOWN') AS disposition,
                               COALESCE(a.duration_sec, 0) AS duration_sec,
                               a.started_at AS started_at,
                               (a.recording_file_id IS NOT NULL) AS has_recording
                        """ + DASHBOARD_FROM + OPERATOR_JOIN, companyId, filter)
                .append("ORDER BY a.started_at DESC LIMIT :limit")
                .bind("limit", limit)
                .prepare(jdbc)
                .query((rs, n) -> new DashboardRecentCall(
                        rs.getLong("call_id"),
                        rs.getString("phone"),
                        rs.getString("client_name"),
                        rs.getString("campaign_name"),
                        rs.getString("operator_name"),
                        rs.getString("direction"),
                        rs.getString("disposition"),
                        rs.getInt("duration_sec"),
                        instantOrNull(rs, "started_at"),
                        rs.getBoolean("has_recording")))
                .list();
    }
}
