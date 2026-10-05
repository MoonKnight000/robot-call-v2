package uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.report.application.port.output.ContactCenterReportRepository;
import uz.murodjon.robotcallv2.report.domain.entity.OperatorPerformance;
import uz.murodjon.robotcallv2.report.domain.entity.QueuePerformance;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter.ReportSql.*;

/** Queue and operator reports over {@code call_attempt}, {@code call_event} and {@code operator_status_log}. */
@Component
public class ContactCenterReportRepositoryAdapter implements ContactCenterReportRepository {

    private final JdbcClient jdbc;

    public ContactCenterReportRepositoryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    /** One read-only transaction for the event counts and the per-queue figures. */
    @Override
    @Transactional(readOnly = true)
    public List<QueuePerformance> findQueuePerformance(long companyId, Instant from, Instant to) {
        Map<String, Object> params = periodParams(companyId, from, to);
        Map<Long, long[]> eventCounts = new HashMap<>();
        jdbc.sql("SELECT e.queue_id AS queue_id, "
                        + "count(*) FILTER (WHERE e.type = 'OVERFLOW') AS overflowed, "
                        + "count(*) FILTER (WHERE e.type = 'CALLBACK_REQUESTED') AS callbacks "
                        + "FROM call_event e WHERE e.company_id = :companyId AND e.at >= :from AND e.at < :to "
                        + "AND e.type IN ('OVERFLOW', 'CALLBACK_REQUESTED') AND e.queue_id IS NOT NULL "
                        + "GROUP BY 1")
                .params(params)
                .query((ResultSet rs) -> {
                    eventCounts.put(rs.getLong("queue_id"), new long[]{rs.getLong("overflowed"), rs.getLong("callbacks")});
                });
        return jdbc.sql("SELECT q.id AS queue_id, q.name AS queue_name, "
                        + "count(a.id) AS offered, "
                        + "count(a.operator_answered_at) AS answered, "
                        + "count(a.id) FILTER (WHERE a.abandoned) AS abandoned, "
                        + "count(a.id) FILTER (WHERE a.abandoned AND coalesce(a.wait_seconds, 0) < 5) AS short_abandoned, "
                        + "count(a.id) FILTER (WHERE a.operator_answered_at IS NOT NULL "
                        + "  AND a.wait_seconds <= q.service_level_sec) AS within_sla, "
                        + "avg(a.wait_seconds) FILTER (WHERE a.operator_answered_at IS NOT NULL) AS avg_wait, "
                        + "max(a.wait_seconds) AS max_wait, "
                        + "avg(extract(epoch FROM (a.ended_at - a.operator_answered_at))) "
                        + "  FILTER (WHERE a.operator_answered_at IS NOT NULL AND a.ended_at IS NOT NULL) AS avg_talk, "
                        + "avg(a.survey_score) AS avg_score "
                        + "FROM call_queue q "
                        + "LEFT JOIN call_attempt a ON a.queue_id = q.id AND a.enqueued_at >= :from AND a.enqueued_at < :to "
                        + "WHERE q.company_id = :companyId "
                        + "GROUP BY q.id, q.name ORDER BY q.name")
                .params(params)
                .query((rs, n) -> {
                    long queueId = rs.getLong("queue_id");
                    long offered = rs.getLong("offered");
                    long abandoned = rs.getLong("abandoned");
                    long shortAbandoned = rs.getLong("short_abandoned");
                    long withinSla = rs.getLong("within_sla");
                    long counted = offered - shortAbandoned;
                    long[] events = eventCounts.getOrDefault(queueId, new long[]{0, 0});
                    return new QueuePerformance(queueId, rs.getString("queue_name"), offered, rs.getLong("answered"),
                            abandoned, shortAbandoned, withinSla,
                            counted > 0 ? withinSla * 100.0 / counted : null,
                            counted > 0 ? (abandoned - shortAbandoned) * 100.0 / counted : null,
                            doubleOrNull(rs, "avg_wait"),
                            intOrNull(rs, "max_wait"),
                            doubleOrNull(rs, "avg_talk"),
                            events[0], events[1],
                            doubleOrNull(rs, "avg_score"));
                })
                .list();
    }

    /** One read-only transaction for the misses, the time in status and the per-operator figures. */
    @Override
    @Transactional(readOnly = true)
    public List<OperatorPerformance> findOperatorPerformance(long companyId, Instant from, Instant to) {
        Map<String, Object> params = periodParams(companyId, from, to);
        Map<Long, Long> misses = new HashMap<>();
        jdbc.sql("SELECT e.user_id AS user_id, count(*) AS misses FROM call_event e "
                        + "WHERE e.company_id = :companyId AND e.type = 'OPERATOR_MISSED' "
                        + "AND e.at >= :from AND e.at < :to AND e.user_id IS NOT NULL GROUP BY 1")
                .params(params)
                .query((ResultSet rs) -> {
                    misses.put(rs.getLong("user_id"), rs.getLong("misses"));
                });
        Map<Long, Map<String, Long>> statusSeconds = new HashMap<>();
        jdbc.sql("SELECT l.user_id AS user_id, l.status AS status, "
                        + "sum(extract(epoch FROM (least(coalesce(l.ended_at, now()), CAST(:to AS timestamptz)) "
                        + "  - greatest(l.started_at, CAST(:from AS timestamptz))))) AS seconds "
                        + "FROM operator_status_log l "
                        + "WHERE l.company_id = :companyId AND l.started_at < :to AND coalesce(l.ended_at, now()) > :from "
                        + "GROUP BY l.user_id, l.status")
                .params(params)
                .query((ResultSet rs) -> {
                    Double seconds = doubleOrNull(rs, "seconds");
                    statusSeconds.computeIfAbsent(rs.getLong("user_id"), id -> new HashMap<>())
                            .put(rs.getString("status"), seconds == null ? 0L : Math.round(seconds));
                });
        return jdbc.sql("SELECT u.id AS user_id, u.name AS user_name, count(a.id) AS answered, "
                        + "avg(extract(epoch FROM (a.ended_at - a.operator_answered_at))) AS avg_talk, "
                        + "coalesce(sum(extract(epoch FROM (a.ended_at - a.operator_answered_at))), 0) AS total_talk, "
                        + "avg(a.survey_score) AS avg_score "
                        + "FROM operator_sip_account s JOIN app_user u ON u.id = s.user_id "
                        + "LEFT JOIN call_attempt a ON a.operator_user_id = u.id AND a.company_id = s.company_id "
                        + "  AND a.operator_answered_at >= :from "
                        + "  AND a.operator_answered_at < :to AND a.ended_at IS NOT NULL "
                        + "WHERE s.company_id = :companyId "
                        + "GROUP BY u.id, u.name ORDER BY u.name")
                .params(params)
                .query((rs, n) -> {
                    long userId = rs.getLong("user_id");
                    Map<String, Long> seconds = statusSeconds.getOrDefault(userId, Map.of());
                    long available = seconds.getOrDefault("AVAILABLE", 0L) + seconds.getOrDefault("RINGING", 0L);
                    long onCall = seconds.getOrDefault("ON_CALL", 0L);
                    long wrapUp = seconds.getOrDefault("WRAP_UP", 0L);
                    long signedIn = available + onCall + wrapUp;
                    return new OperatorPerformance(userId, rs.getString("user_name"), rs.getLong("answered"),
                            misses.getOrDefault(userId, 0L),
                            doubleOrNull(rs, "avg_talk"), Math.round(rs.getDouble("total_talk")),
                            doubleOrNull(rs, "avg_score"),
                            available, onCall, wrapUp, seconds.getOrDefault("BREAK", 0L),
                            signedIn > 0 ? (onCall + wrapUp) * 100.0 / signedIn : null);
                })
                .list();
    }

    /** The company and a [from, to) period, the bounds both reports share. */
    private static Map<String, Object> periodParams(long companyId, Instant from, Instant to) {
        Map<String, Object> params = new HashMap<>();
        params.put("companyId", companyId);
        params.put("from", toTimestamptz(from));
        params.put("to", toTimestamptz(to));
        return params;
    }
}
