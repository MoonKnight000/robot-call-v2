package uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.contact.application.dto.ContactCallHistoryRow;
import uz.murodjon.robotcallv2.report.application.port.output.ReportRepository;
import uz.murodjon.robotcallv2.report.domain.entity.*;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.dialog.ReasonCode;
import uz.murodjon.robotcallv2.shared.dialog.Sentiment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ReportRepositoryAdapter implements ReportRepository {

    private static final String CALL_SELECT = """
            SELECT a.id            AS call_id,
                   a.target_id     AS target_id,
                   -- The attempt's own number since V4; campaign_target is the fallback
                   -- for rows written before it. A manual or inbound call's target is a
                   -- shared placeholder whose phone reads 'MANUAL'/'INBOUND'.
                   COALESCE(a.phone, t.phone) AS phone,
                   a.language      AS language,
                   a.started_at    AS started_at,
                   a.ended_at      AS ended_at,
                   a.duration_sec  AS duration_sec,
                   a.disposition   AS disposition,
                   a.hangup_cause  AS hangup_cause,
                   a.recording_file_id AS recording_file_id,
                   r.summary       AS summary,
                   r.promised_date AS promised_date,
                   r.promised_amount AS promised_amount,
                   r.crm_note_id   AS crm_note_id,
                   t.context_data ->> 'clientName' AS client_name,
                   c.name          AS campaign_name,
                   u.name          AS operator_name
            FROM call_attempt a
            LEFT JOIN campaign_target t ON t.id = a.target_id
            LEFT JOIN campaign c ON c.id = t.campaign_id
            LEFT JOIN call_result r ON r.call_id = a.id
            LEFT JOIN app_user u ON u.id = a.operator_user_id
            """;

    private final EntityManager em;

    public ReportRepositoryAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public CampaignStats campaignStats(long companyId, long campaignId) {
        List<Object[]> campaign = rows(em.createNativeQuery(
                        "SELECT name, status FROM campaign WHERE id = :campaignId AND company_id = :companyId")
                .setParameter("campaignId", campaignId)
                .setParameter("companyId", companyId));
        if (campaign.isEmpty()) {
            return null;
        }
        Object[] campaignRow = campaign.get(0);

        Map<String, Long> targets = countBy(
                "SELECT status AS k, count(*) AS n FROM campaign_target WHERE campaign_id = :campaignId GROUP BY status",
                campaignId);
        Map<String, Long> dispositions = countBy(
                "SELECT coalesce(a.disposition, 'UNKNOWN') AS k, count(*) AS n "
                        + "FROM call_attempt a JOIN campaign_target t ON t.id = a.target_id "
                        + "WHERE t.campaign_id = :campaignId AND a.ended_at IS NOT NULL "
                        + "GROUP BY coalesce(a.disposition, 'UNKNOWN')",
                campaignId);

        long finished = dispositions.values().stream().mapToLong(Long::longValue).sum();
        long promises = dispositions.getOrDefault(Disposition.PROMISE_TO_PAY.name(), 0L);
        long escalated = dispositions.getOrDefault(Disposition.TRANSFERRED.name(), 0L);

        Object avgResult = em.createNativeQuery(
                        "SELECT avg(a.duration_sec) FROM call_attempt a JOIN campaign_target t ON t.id = a.target_id "
                                + "WHERE t.campaign_id = :campaignId AND a.duration_sec IS NOT NULL")
                .setParameter("campaignId", campaignId)
                .getSingleResult();

        return new CampaignStats(
                campaignId,
                (String) campaignRow[0],
                asEnumOrNull(CampaignStatus.class, campaignRow[1]),
                targets,
                dispositions,
                finished,
                finished == 0 ? 0d : (double) promises / finished,
                asDoubleOrNull(avgResult),
                escalated);
    }

    @Override
    public InboundRouteStats inboundRouteStats(long companyId, long inboundRouteId) {
        List<Object[]> route = rows(em.createNativeQuery(
                        "SELECT did_number FROM inbound_route WHERE id = :routeId AND company_id = :companyId")
                .setParameter("routeId", inboundRouteId)
                .setParameter("companyId", companyId));
        if (route.isEmpty()) {
            return null;
        }
        String didNumber = (String) route.get(0)[0];

        Object[] totals = (Object[]) em.createNativeQuery(
                        "SELECT count(*) AS total_calls, "
                                + "count(*) FILTER (WHERE duration_sec IS NOT NULL) AS answered_calls, "
                                + "avg(duration_sec) FILTER (WHERE duration_sec IS NOT NULL) AS avg_duration_sec, "
                                + "max(started_at) AS last_call_at "
                                + "FROM call_attempt WHERE inbound_route_id = :routeId AND company_id = :companyId")
                .setParameter("routeId", inboundRouteId)
                .setParameter("companyId", companyId)
                .getSingleResult();
        long totalCalls = asLong(totals[0]);
        long answeredCalls = asLong(totals[1]);

        Map<String, Long> dispositions = new LinkedHashMap<>();
        for (Object[] row : rows(em.createNativeQuery(
                        "SELECT coalesce(disposition, 'UNKNOWN') AS k, count(*) AS n FROM call_attempt "
                                + "WHERE inbound_route_id = :routeId AND company_id = :companyId AND ended_at IS NOT NULL "
                                + "GROUP BY coalesce(disposition, 'UNKNOWN')")
                .setParameter("routeId", inboundRouteId)
                .setParameter("companyId", companyId))) {
            dispositions.put((String) row[0], asLong(row[1]));
        }

        Object earliest = em.createNativeQuery(
                        "SELECT min(started_at) FROM call_attempt "
                                + "WHERE company_id = :companyId AND inbound_route_id IS NOT NULL")
                .setParameter("companyId", companyId)
                .getSingleResult();

        return new InboundRouteStats(inboundRouteId, didNumber, totalCalls, answeredCalls,
                rate(answeredCalls, totalCalls), asDoubleOrNull(totals[2]), asInstantOrNull(totals[3]), dispositions,
                asInstantOrNull(earliest));
    }

    @Override
    public List<CallRow> callsOfCampaign(long companyId, long campaignId, CallFilter filter) {
        List<Object[]> result = rows(em.createNativeQuery(CALL_SELECT
                        + " WHERE t.campaign_id = :campaignId AND a.company_id = :companyId"
                        + filter.orderByClause() + " LIMIT :limit OFFSET :offset")
                .setParameter("campaignId", campaignId)
                .setParameter("companyId", companyId)
                .setParameter("limit", filter.sizeOrDefault())
                .setParameter("offset", filter.offset()));
        return result.stream().map(ReportRepositoryAdapter::toCallRow).toList();
    }

    @Override
    public long countCallsOfCampaign(long companyId, long campaignId) {
        Object result = em.createNativeQuery(
                        "SELECT count(*) FROM call_attempt a JOIN campaign_target t ON t.id = a.target_id "
                                + "WHERE t.campaign_id = :campaignId AND a.company_id = :companyId")
                .setParameter("campaignId", campaignId)
                .setParameter("companyId", companyId)
                .getSingleResult();
        return asLong(result);
    }

    @Override
    public List<CallRow> recentCalls(long companyId, CallFilter filter) {
        StringBuilder sql = new StringBuilder(CALL_SELECT).append(" WHERE a.company_id = :companyId");
        appendCallFilterWhere(sql, filter);
        sql.append(filter.orderByClause()).append(" LIMIT :limit OFFSET :offset");
        Query query = em.createNativeQuery(sql.toString())
                .setParameter("companyId", companyId)
                .setParameter("limit", filter.sizeOrDefault())
                .setParameter("offset", filter.offset());
        bindCallFilterParams(query, filter);
        return rows(query).stream().map(ReportRepositoryAdapter::toCallRow).toList();
    }

    @Override
    public List<CallRow> exportCalls(long companyId, CallFilter filter) {
        StringBuilder sql = new StringBuilder(CALL_SELECT).append(" WHERE a.company_id = :companyId");
        appendCallFilterWhere(sql, filter);
        sql.append(filter.orderByClause()).append(" LIMIT :limit");
        Query query = em.createNativeQuery(sql.toString())
                .setParameter("companyId", companyId)
                .setParameter("limit", FilterInterface.MAX_SIZE);
        bindCallFilterParams(query, filter);
        return rows(query).stream().map(ReportRepositoryAdapter::toCallRow).toList();
    }

    @Override
    public long countRecentCalls(long companyId, CallFilter filter) {
        StringBuilder sql = new StringBuilder(
                "SELECT count(*) FROM call_attempt a "
                        + "LEFT JOIN campaign_target t ON t.id = a.target_id "
                        + "LEFT JOIN campaign c ON c.id = t.campaign_id "
                        + "WHERE a.company_id = :companyId");
        appendCallFilterWhere(sql, filter);
        Query query = em.createNativeQuery(sql.toString()).setParameter("companyId", companyId);
        bindCallFilterParams(query, filter);
        return asLong(query.getSingleResult());
    }

    private static void appendCallFilterWhere(StringBuilder sql, CallFilter filter) {
        if (filter.ids() != null && !filter.ids().isEmpty()) {
            sql.append(" AND a.id IN (:ids)");
            return;
        }
        if (filter.q() != null && !filter.q().isBlank()) {
            sql.append(" AND COALESCE(a.phone, t.phone) LIKE :q");
        }
        if (filter.campaignId() != null) {
            sql.append(" AND t.campaign_id = :campaignId");
        }
        if (filter.disposition() != null) {
            sql.append(" AND a.disposition = :disposition");
        }
        if (filter.dateFrom() != null) {
            sql.append(" AND a.started_at >= :dateFrom");
        }
        if (filter.dateTo() != null) {
            sql.append(" AND a.started_at < :dateTo");
        }
        if (filter.durationMinSec() != null) {
            sql.append(" AND a.duration_sec >= :durationMinSec");
        }
        if (filter.durationMaxSec() != null) {
            sql.append(" AND a.duration_sec <= :durationMaxSec");
        }
    }

    private static void bindCallFilterParams(Query query, CallFilter filter) {
        if (filter.ids() != null && !filter.ids().isEmpty()) {
            query.setParameter("ids", filter.ids());
            return;
        }
        if (filter.q() != null && !filter.q().isBlank()) {
            query.setParameter("q", "%" + filter.q().trim() + "%");
        }
        if (filter.campaignId() != null) {
            query.setParameter("campaignId", filter.campaignId());
        }
        if (filter.disposition() != null) {
            query.setParameter("disposition", filter.disposition().name());
        }
        if (filter.dateFrom() != null) {
            query.setParameter("dateFrom", filter.dateFrom());
        }
        if (filter.dateTo() != null) {
            query.setParameter("dateTo", filter.dateTo());
        }
        if (filter.durationMinSec() != null) {
            query.setParameter("durationMinSec", filter.durationMinSec());
        }
        if (filter.durationMaxSec() != null) {
            query.setParameter("durationMaxSec", filter.durationMaxSec());
        }
    }

    @Override
    public CallRow findCall(long companyId, long callId) {
        List<Object[]> result = rows(em.createNativeQuery(
                        CALL_SELECT + " WHERE a.id = :callId AND a.company_id = :companyId")
                .setParameter("callId", callId)
                .setParameter("companyId", companyId));
        return result.isEmpty() ? null : toCallRow(result.get(0));
    }

    @Override
    public CallDetail callDetail(long companyId, long callId) {
        CallRow callRow = findCall(companyId, callId);
        if (callRow == null) {
            return null;
        }

        List<Object[]> transcriptRows = rows(em.createNativeQuery(
                        "SELECT seq, role, text, dialog_state, ts_offset_ms, stt_confidence "
                                + "FROM call_transcript WHERE call_id = :callId ORDER BY seq")
                .setParameter("callId", callId));
        List<TranscriptLine> transcript = transcriptRows.stream()
                .map(r -> new TranscriptLine(
                        asInt(r[0]), (String) r[1], (String) r[2], (String) r[3], asInt(r[4]), asFloatOrNull(r[5])))
                .toList();

        List<Object[]> extraRows = rows(em.createNativeQuery(
                        "SELECT r.reason_code, r.sentiment, r.needs_follow_up, r.follow_up_note, r.escalated, "
                                + "a.error_message "
                                + "FROM call_attempt a LEFT JOIN call_result r ON r.call_id = a.id WHERE a.id = :callId")
                .setParameter("callId", callId));
        Object[] e = extraRows.isEmpty() ? new Object[6] : extraRows.get(0);
        return new CallDetail(
                callRow,
                transcript,
                asEnumOrNull(ReasonCode.class, e[0]),
                asEnumOrNull(Sentiment.class, e[1]),
                Boolean.TRUE.equals(e[2]),
                (String) e[3],
                Boolean.TRUE.equals(e[4]),
                (String) e[5],
                technicalDetail(callId));
    }

    private CallTechnicalDetail technicalDetail(long callId) {
        List<Object[]> rows = rows(em.createNativeQuery(
                        "SELECT channel_name, trunk, amd_result, stt_provider, tts_provider, tts_voice, "
                                + "llm_model, prompt_tokens, completion_tokens, cached_tokens, turn_count, "
                                + "avg_turn_latency_ms, max_turn_latency_ms, avg_llm_latency_ms, max_llm_latency_ms "
                                + "FROM call_technical WHERE call_id = :callId")
                .setParameter("callId", callId));
        if (rows.isEmpty()) {
            return null;
        }
        Object[] t = rows.get(0);
        return new CallTechnicalDetail(
                (String) t[0], (String) t[1], (String) t[2], (String) t[3], (String) t[4], (String) t[5],
                (String) t[6],
                t[7] == null ? null : asInt(t[7]),
                t[8] == null ? null : asInt(t[8]),
                t[9] == null ? null : asInt(t[9]),
                t[10] == null ? null : asInt(t[10]),
                t[11] == null ? null : asInt(t[11]),
                t[12] == null ? null : asInt(t[12]),
                t[13] == null ? null : asInt(t[13]),
                t[14] == null ? null : asInt(t[14]));
    }

    @Override
    public DashboardTotals dashboardTotals(long companyId, Instant from, Instant to, Long campaignId) {
        StringBuilder sql = new StringBuilder(
                "SELECT count(*) AS total_calls, "
                        + "count(*) FILTER (WHERE a.duration_sec IS NOT NULL) AS answered_calls, "
                        + "avg(a.duration_sec) FILTER (WHERE a.duration_sec IS NOT NULL) AS avg_duration_sec, "
                        + "count(*) FILTER (WHERE a.disposition = 'PROMISE_TO_PAY') AS promises "
                        + "FROM call_attempt a JOIN campaign_target t ON t.id = a.target_id "
                        + "WHERE a.company_id = :companyId AND a.started_at >= :from AND a.started_at < :to");
        if (campaignId != null) {
            sql.append(" AND t.campaign_id = :campaignId");
        }
        Query query = em.createNativeQuery(sql.toString())
                .setParameter("companyId", companyId)
                .setParameter("from", from)
                .setParameter("to", to);
        if (campaignId != null) {
            query.setParameter("campaignId", campaignId);
        }
        Object[] row = (Object[]) query.getSingleResult();
        return new DashboardTotals(asLong(row[0]), asLong(row[1]), asDoubleOrNull(row[2]), asLong(row[3]));
    }

    @Override
    public DashboardTotals operatorTotals(long companyId, Instant from, Instant to, long operatorUserId) {
        Object[] row = (Object[]) em.createNativeQuery(
                        "SELECT count(*) AS total_calls, "
                                + "count(*) FILTER (WHERE duration_sec IS NOT NULL) AS answered_calls, "
                                + "avg(duration_sec) FILTER (WHERE duration_sec IS NOT NULL) AS avg_duration_sec, "
                                + "count(*) FILTER (WHERE disposition = 'PROMISE_TO_PAY') AS promises "
                                + "FROM call_attempt "
                                + "WHERE company_id = :companyId AND operator_user_id = :operatorUserId "
                                + "AND started_at >= :from AND started_at < :to")
                .setParameter("companyId", companyId)
                .setParameter("operatorUserId", operatorUserId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();
        return new DashboardTotals(asLong(row[0]), asLong(row[1]), asDoubleOrNull(row[2]), asLong(row[3]));
    }

    @Override
    public List<DashboardBucket> dashboardBuckets(long companyId, Instant from, Instant to, Long campaignId,
                                                  String granularity) {
        StringBuilder sql = new StringBuilder(
                "SELECT date_trunc(:granularity, a.started_at) AS bucket, "
                        + "count(*) AS total, "
                        + "count(*) FILTER (WHERE a.duration_sec IS NOT NULL) AS answered, "
                        + "count(*) FILTER (WHERE a.disposition = 'NO_ANSWER') AS no_answer, "
                        + "count(*) FILTER (WHERE a.disposition = 'FAILED') AS error_count, "
                        + "avg(a.duration_sec) FILTER (WHERE a.duration_sec IS NOT NULL) AS avg_duration_sec, "
                        + "count(*) FILTER (WHERE a.disposition = 'PROMISE_TO_PAY') AS promises "
                        + "FROM call_attempt a JOIN campaign_target t ON t.id = a.target_id "
                        + "WHERE a.company_id = :companyId AND a.started_at >= :from AND a.started_at < :to");
        if (campaignId != null) {
            sql.append(" AND t.campaign_id = :campaignId");
        }
        sql.append(" GROUP BY bucket ORDER BY bucket");
        Query query = em.createNativeQuery(sql.toString())
                .setParameter("granularity", granularity)
                .setParameter("companyId", companyId)
                .setParameter("from", from)
                .setParameter("to", to);
        if (campaignId != null) {
            query.setParameter("campaignId", campaignId);
        }
        return rows(query).stream()
                .map(r -> new DashboardBucket(
                        asInstant(r[0]), asLong(r[1]), asLong(r[2]), asLong(r[3]), asLong(r[4]),
                        asDoubleOrNull(r[5]), asLong(r[6])))
                .toList();
    }

    @Override
    public List<DashboardBucket> dynamicsBuckets(long companyId, Instant from, Instant to, Long campaignId, Long scenarioId,
                                                 Boolean escalated, String granularity) {
        StringBuilder sql = new StringBuilder(
                "SELECT date_trunc(:granularity, a.started_at) AS bucket, "
                        + "count(*) AS total, "
                        + "count(*) FILTER (WHERE a.duration_sec IS NOT NULL) AS answered, "
                        + "count(*) FILTER (WHERE a.disposition = 'NO_ANSWER') AS no_answer, "
                        + "count(*) FILTER (WHERE a.disposition = 'FAILED') AS error_count, "
                        + "avg(a.duration_sec) FILTER (WHERE a.duration_sec IS NOT NULL) AS avg_duration_sec, "
                        + "count(*) FILTER (WHERE a.disposition = 'PROMISE_TO_PAY') AS promises "
                        + "FROM call_attempt a "
                        + "JOIN campaign_target t ON t.id = a.target_id "
                        + "LEFT JOIN campaign c ON c.id = t.campaign_id "
                        + "LEFT JOIN ai_agent ag ON ag.id = c.ai_agent_id "
                        + "LEFT JOIN call_result r ON r.call_id = a.id "
                        + "WHERE a.company_id = :companyId AND a.started_at >= :from AND a.started_at < :to");
        if (campaignId != null) {
            sql.append(" AND t.campaign_id = :campaignId");
        }
        if (scenarioId != null) {
            sql.append(" AND ag.scenario_id = :scenarioId");
        }
        if (escalated != null) {
            sql.append(" AND coalesce(r.escalated, false) = :escalated");
        }
        sql.append(" GROUP BY bucket ORDER BY bucket");
        Query query = em.createNativeQuery(sql.toString())
                .setParameter("granularity", granularity)
                .setParameter("companyId", companyId)
                .setParameter("from", from)
                .setParameter("to", to);
        if (campaignId != null) {
            query.setParameter("campaignId", campaignId);
        }
        if (scenarioId != null) {
            query.setParameter("scenarioId", scenarioId);
        }
        if (escalated != null) {
            query.setParameter("escalated", escalated);
        }
        return rows(query).stream()
                .map(r -> new DashboardBucket(
                        asInstant(r[0]), asLong(r[1]), asLong(r[2]), asLong(r[3]), asLong(r[4]),
                        asDoubleOrNull(r[5]), asLong(r[6])))
                .toList();
    }

    @Override
    public List<DashboardOutcome> dashboardOutcomes(long companyId, Instant from, Instant to, Long campaignId) {
        StringBuilder sql = new StringBuilder(
                "SELECT coalesce(a.disposition, 'UNKNOWN') AS disposition, count(*) AS n "
                        + "FROM call_attempt a JOIN campaign_target t ON t.id = a.target_id "
                        + "WHERE a.company_id = :companyId AND a.started_at >= :from AND a.started_at < :to");
        if (campaignId != null) {
            sql.append(" AND t.campaign_id = :campaignId");
        }
        sql.append(" GROUP BY coalesce(a.disposition, 'UNKNOWN') ORDER BY n DESC");
        Query query = em.createNativeQuery(sql.toString())
                .setParameter("companyId", companyId)
                .setParameter("from", from)
                .setParameter("to", to);
        if (campaignId != null) {
            query.setParameter("campaignId", campaignId);
        }
        return rows(query).stream()
                .map(r -> new DashboardOutcome((String) r[0], asLong(r[1])))
                .toList();
    }

    @Override
    public List<HourlyHeatmapCell> hourlyHeatmap(long companyId, Instant from, Instant to, Long campaignId) {
        StringBuilder sql = new StringBuilder(
                "SELECT extract(dow from a.started_at)::int AS day_of_week, "
                        + "extract(hour from a.started_at)::int AS hour_of_day, "
                        + "count(*) AS total, "
                        + "count(*) FILTER (WHERE a.duration_sec IS NOT NULL) AS answered "
                        + "FROM call_attempt a JOIN campaign_target t ON t.id = a.target_id "
                        + "WHERE a.company_id = :companyId AND a.started_at >= :from AND a.started_at < :to");
        if (campaignId != null) {
            sql.append(" AND t.campaign_id = :campaignId");
        }
        sql.append(" GROUP BY day_of_week, hour_of_day ORDER BY day_of_week, hour_of_day");
        Query query = em.createNativeQuery(sql.toString())
                .setParameter("companyId", companyId)
                .setParameter("from", from)
                .setParameter("to", to);
        if (campaignId != null) {
            query.setParameter("campaignId", campaignId);
        }
        return rows(query).stream()
                .map(r -> {
                    long total = asLong(r[2]);
                    long answered = asLong(r[3]);
                    return new HourlyHeatmapCell(asInt(r[0]), asInt(r[1]), total, answered, rate(answered, total));
                })
                .toList();
    }

    @Override
    public List<CampaignComparisonRow> campaignComparison(long companyId, Instant from, Instant to,
                                                          List<Long> campaignIds) {
        StringBuilder sql = new StringBuilder(
                "SELECT c.id AS campaign_id, c.name AS campaign_name, "
                        + "count(*) AS total_calls, "
                        + "count(*) FILTER (WHERE a.duration_sec IS NOT NULL) AS answered_calls, "
                        + "avg(a.duration_sec) FILTER (WHERE a.duration_sec IS NOT NULL) AS avg_duration_sec, "
                        + "count(*) FILTER (WHERE a.disposition = 'PROMISE_TO_PAY') AS promises "
                        + "FROM call_attempt a "
                        + "JOIN campaign_target t ON t.id = a.target_id "
                        + "JOIN campaign c ON c.id = t.campaign_id "
                        + "WHERE a.company_id = :companyId AND a.started_at >= :from AND a.started_at < :to");
        if (campaignIds != null && !campaignIds.isEmpty()) {
            sql.append(" AND c.id IN (:campaignIds)");
        }
        sql.append(" GROUP BY c.id, c.name ORDER BY total_calls DESC");
        Query query = em.createNativeQuery(sql.toString())
                .setParameter("companyId", companyId)
                .setParameter("from", from)
                .setParameter("to", to);
        if (campaignIds != null && !campaignIds.isEmpty()) {
            query.setParameter("campaignIds", campaignIds);
        }
        return rows(query).stream()
                .map(r -> {
                    long total = asLong(r[2]);
                    long answered = asLong(r[3]);
                    return new CampaignComparisonRow(
                            asLong(r[0]), (String) r[1], total, answered, rate(answered, total),
                            asDoubleOrNull(r[4]), asLong(r[5]));
                })
                .toList();
    }

    private static final int[] DURATION_BUCKET_BOUNDARIES = {30, 60, 120, 300, 600};

    @Override
    public List<DurationHistogramBucket> durationHistogram(long companyId, Instant from, Instant to, Long campaignId) {
        StringBuilder sql = new StringBuilder(
                "SELECT CASE "
                        + "WHEN a.duration_sec < 30 THEN '0-30' "
                        + "WHEN a.duration_sec < 60 THEN '30-60' "
                        + "WHEN a.duration_sec < 120 THEN '60-120' "
                        + "WHEN a.duration_sec < 300 THEN '120-300' "
                        + "WHEN a.duration_sec < 600 THEN '300-600' "
                        + "ELSE '600+' END AS bucket_label, "
                        + "count(*) AS n "
                        + "FROM call_attempt a JOIN campaign_target t ON t.id = a.target_id "
                        + "WHERE a.company_id = :companyId AND a.duration_sec IS NOT NULL "
                        + "AND a.started_at >= :from AND a.started_at < :to");
        if (campaignId != null) {
            sql.append(" AND t.campaign_id = :campaignId");
        }
        sql.append(" GROUP BY bucket_label");
        Query query = em.createNativeQuery(sql.toString())
                .setParameter("companyId", companyId)
                .setParameter("from", from)
                .setParameter("to", to);
        if (campaignId != null) {
            query.setParameter("campaignId", campaignId);
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : rows(query)) {
            counts.put((String) row[0], asLong(row[1]));
        }
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
    public List<FunnelStage> funnel(long companyId, Instant from, Instant to, Long campaignId) {
        StringBuilder sql = new StringBuilder(
                "SELECT count(*) AS calls, "
                        + "count(*) FILTER (WHERE a.duration_sec IS NOT NULL) AS answered, "
                        + "count(*) FILTER (WHERE a.duration_sec IS NOT NULL "
                        + "  AND (a.disposition IS NULL OR a.disposition <> 'WRONG_NUMBER')) AS person_confirmed, "
                        + "count(*) FILTER (WHERE a.duration_sec IS NOT NULL "
                        + "  AND (a.disposition IS NULL OR a.disposition <> 'WRONG_NUMBER') "
                        + "  AND coalesce(ct.turn_count, 0) > 0) AS conversation, "
                        + "count(*) FILTER (WHERE a.disposition IN "
                        + "  ('PROMISE_TO_PAY', 'REFUSED', 'TRANSFERRED', 'COMPLETED', 'DO_NOT_CALL')) AS result "
                        + "FROM call_attempt a "
                        + "JOIN campaign_target t ON t.id = a.target_id "
                        + "LEFT JOIN call_technical ct ON ct.call_id = a.id "
                        + "WHERE a.company_id = :companyId AND a.started_at >= :from AND a.started_at < :to");
        if (campaignId != null) {
            sql.append(" AND t.campaign_id = :campaignId");
        }
        Query query = em.createNativeQuery(sql.toString())
                .setParameter("companyId", companyId)
                .setParameter("from", from)
                .setParameter("to", to);
        if (campaignId != null) {
            query.setParameter("campaignId", campaignId);
        }
        Object[] row = (Object[]) query.getSingleResult();
        long calls = asLong(row[0]);
        return List.of(
                new FunnelStage("CALL", calls, rate(calls, calls)),
                new FunnelStage("ANSWERED", asLong(row[1]), rate(asLong(row[1]), calls)),
                new FunnelStage("PERSON_CONFIRMED", asLong(row[2]), rate(asLong(row[2]), calls)),
                new FunnelStage("CONVERSATION", asLong(row[3]), rate(asLong(row[3]), calls)),
                new FunnelStage("RESULT", asLong(row[4]), rate(asLong(row[4]), calls)));
    }

    private static double rate(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    @Override
    public List<ContactCallHistoryRow> callsForPhone(long companyId, String phone, int limit) {
        List<Object[]> result = rows(em.createNativeQuery(
                        "SELECT a.id AS call_id, cmp.name AS campaign_name, a.started_at AS started_at, "
                                + "a.duration_sec AS duration_sec, a.disposition AS disposition "
                                + "FROM call_attempt a "
                                + "JOIN campaign_target t ON t.id = a.target_id "
                                + "LEFT JOIN campaign cmp ON cmp.id = t.campaign_id "
                                + "WHERE COALESCE(a.phone, t.phone) = :phone AND a.company_id = :companyId "
                                + "ORDER BY a.started_at DESC NULLS LAST LIMIT :limit")
                .setParameter("phone", phone)
                .setParameter("companyId", companyId)
                .setParameter("limit", limit));
        return result.stream()
                .map(r -> new ContactCallHistoryRow(
                        asLong(r[0]), (String) r[1], asInstantOrNull(r[2]),
                        r[3] == null ? null : asInt(r[3]), (String) r[4]))
                .toList();
    }

    @Override
    public Long recordingFileId(long companyId, long callId) {
        List<Object> result = em.createNativeQuery(
                        "SELECT recording_file_id FROM call_attempt WHERE id = :callId AND company_id = :companyId")
                .setParameter("callId", callId)
                .setParameter("companyId", companyId)
                .getResultList();
        return result.isEmpty() || result.get(0) == null ? null : ((Number) result.get(0)).longValue();
    }

    /**
     * The join chain every dashboard figure is measured over.
     *
     * <p>A call reaches its agent one of two ways — through the campaign that dialled it,
     * or through the inbound route that answered it — so the agent is joined twice and
     * every join is outer: a widget call or a manual one has neither, and an inner join
     * would quietly drop it from every total on the page.
     */
    private static final String DASHBOARD_AGENT_JOINS = """
            FROM call_attempt a
            LEFT JOIN campaign_target t ON t.id = a.target_id
            LEFT JOIN campaign c ON c.id = t.campaign_id
            LEFT JOIN ai_agent ag ON ag.id = c.ai_agent_id
            LEFT JOIN inbound_route ir ON ir.id = a.inbound_route_id
            LEFT JOIN ai_agent ir_ag ON ir_ag.id = ir.ai_agent_id
            """;

    /** Added by the two figures that name whoever took the call, agent or human. */
    private static final String DASHBOARD_OPERATOR_JOIN =
            "LEFT JOIN app_user u ON u.id = a.operator_user_id\n";

    /** The company and the period, which every dashboard figure is bounded by. */
    private static final String DASHBOARD_PERIOD_WHERE = """
            WHERE a.company_id = :companyId
              AND a.started_at >= :from AND a.started_at < :to
            """;

    /**
     * The dashboard's two optional narrowings. The scenario has to be accepted from either
     * agent — the campaign's or the inbound route's — because a scenario is spoken on calls
     * that arrive both ways, and matching only the outbound side would silently halve an
     * inbound-heavy company's numbers.
     */
    private static void appendDashboardScope(StringBuilder sql, Long campaignId, Long scenarioId) {
        if (campaignId != null) {
            sql.append(" AND t.campaign_id = :campaignId");
        }
        if (scenarioId != null) {
            sql.append(" AND (ag.scenario_id = :scenarioId OR ir_ag.scenario_id = :scenarioId)");
        }
    }

    /** Binds what {@link #DASHBOARD_PERIOD_WHERE} and {@link #appendDashboardScope} asked for. */
    private Query dashboardQuery(String sql, long companyId, Instant from, Instant to,
                                 Long campaignId, Long scenarioId) {
        Query query = em.createNativeQuery(sql)
                .setParameter("companyId", companyId)
                .setParameter("from", from)
                .setParameter("to", to);
        if (campaignId != null) {
            query.setParameter("campaignId", campaignId);
        }
        if (scenarioId != null) {
            query.setParameter("scenarioId", scenarioId);
        }
        return query;
    }

    @Override
    public DashboardAggregates dashboardAggregates(long companyId, Instant from, Instant to, Long campaignId,
                                                   Long scenarioId) {
        StringBuilder sql = new StringBuilder("""
                SELECT 
                    COUNT(a.id) AS total_calls,
                    COALESCE(SUM(a.duration_sec), 0) / 60 AS total_minutes,
                    COALESCE(AVG(a.duration_sec) FILTER (WHERE a.duration_sec IS NOT NULL AND a.duration_sec > 0), 0) AS avg_duration_sec,
                    COUNT(a.id) FILTER (WHERE a.disposition IN ('COMPLETED', 'PROMISE_TO_PAY')) AS completed_calls,
                    COUNT(a.id) FILTER (WHERE a.disposition IN ('FAILED', 'CARRIER_REJECTED')) AS failed_calls,
                    COUNT(a.id) FILTER (WHERE a.disposition = 'NO_ANSWER') AS missed_calls
                """ + DASHBOARD_AGENT_JOINS + DASHBOARD_PERIOD_WHERE);
        appendDashboardScope(sql, campaignId, scenarioId);
        Query query = dashboardQuery(sql.toString(), companyId, from, to, campaignId, scenarioId);
        Object[] row = (Object[]) query.getSingleResult();
        return new DashboardAggregates(
                asLong(row[0]),
                asLong(row[1]),
                asDoubleOrNull(row[2]) != null ? asDoubleOrNull(row[2]) : 0.0,
                asLong(row[3]),
                asLong(row[4]),
                asLong(row[5]));
    }

    @Override
    public List<DashboardTimelineBucket> dashboardTimelineBuckets(long companyId, Instant from, Instant to,
                                                                  Long campaignId, Long scenarioId,
                                                                  String granularity) {
        StringBuilder sql = new StringBuilder("""
                SELECT 
                    date_trunc(:granularity, a.started_at) AS bucket,
                    COUNT(a.id) AS calls,
                    COALESCE(SUM(a.duration_sec), 0) / 60 AS minutes,
                    COUNT(a.id) FILTER (WHERE a.disposition IN ('FAILED', 'CARRIER_REJECTED')) AS failed,
                    COUNT(a.id) FILTER (WHERE a.disposition IN ('COMPLETED', 'PROMISE_TO_PAY')) AS completed,
                    COUNT(a.id) FILTER (WHERE a.disposition = 'NO_ANSWER') AS missed,
                    COALESCE(AVG(a.duration_sec) FILTER (WHERE a.duration_sec IS NOT NULL AND a.duration_sec > 0), 0) AS avg_duration_sec
                """ + DASHBOARD_AGENT_JOINS + DASHBOARD_PERIOD_WHERE);
        appendDashboardScope(sql, campaignId, scenarioId);
        sql.append(" GROUP BY bucket ORDER BY bucket");
        Query query = dashboardQuery(sql.toString(), companyId, from, to, campaignId, scenarioId)
                .setParameter("granularity", granularity);
        return rows(query).stream()
                .map(r -> new DashboardTimelineBucket(
                        asInstant(r[0]),
                        asLong(r[1]),
                        asLong(r[2]),
                        asLong(r[3]),
                        asLong(r[4]),
                        asLong(r[5]),
                        asDoubleOrNull(r[6]) != null ? asDoubleOrNull(r[6]) : 0.0))
                .toList();
    }

    @Override
    public List<DashboardOutcome> dashboardStatusBreakdown(long companyId, Instant from, Instant to,
                                                           Long campaignId, Long scenarioId) {
        StringBuilder sql = new StringBuilder("""
                SELECT 
                    COALESCE(a.disposition, 'UNKNOWN') AS disposition,
                    COUNT(a.id) AS n
                """ + DASHBOARD_AGENT_JOINS + DASHBOARD_PERIOD_WHERE);
        appendDashboardScope(sql, campaignId, scenarioId);
        sql.append(" GROUP BY COALESCE(a.disposition, 'UNKNOWN') ORDER BY n DESC");
        Query query = dashboardQuery(sql.toString(), companyId, from, to, campaignId, scenarioId);
        return rows(query).stream()
                .map(r -> new DashboardOutcome((String) r[0], asLong(r[1])))
                .toList();
    }

    @Override
    public List<DashboardDirectionRow> dashboardDirectionStats(long companyId, Instant from, Instant to,
                                                               Long campaignId, Long scenarioId) {
        StringBuilder sql = new StringBuilder("""
                SELECT 
                    CASE WHEN a.inbound_route_id IS NOT NULL THEN 'inbound' ELSE 'outbound' END AS direction,
                    COUNT(a.id) AS total_calls,
                    COALESCE(SUM(a.duration_sec), 0) / 60 AS total_minutes,
                    COALESCE(AVG(a.duration_sec) FILTER (WHERE a.duration_sec IS NOT NULL AND a.duration_sec > 0), 0) AS avg_duration_sec,
                    COUNT(a.id) FILTER (WHERE a.disposition IN ('COMPLETED', 'PROMISE_TO_PAY')) AS completed_calls
                """ + DASHBOARD_AGENT_JOINS + DASHBOARD_PERIOD_WHERE);
        appendDashboardScope(sql, campaignId, scenarioId);
        sql.append(" GROUP BY (CASE WHEN a.inbound_route_id IS NOT NULL THEN 'inbound' ELSE 'outbound' END)");
        Query query = dashboardQuery(sql.toString(), companyId, from, to, campaignId, scenarioId);
        return rows(query).stream()
                .map(r -> new DashboardDirectionRow(
                        (String) r[0],
                        asLong(r[1]),
                        asLong(r[2]),
                        asInt(r[3]),
                        asLong(r[4])))
                .toList();
    }

    @Override
    public List<DashboardAgentRow> dashboardTopAgents(long companyId, Instant from, Instant to,
                                                     Long campaignId, Long scenarioId, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT 
                    COALESCE(u.id, ag.id, ir_ag.id, 0) AS agent_id,
                    COALESCE(u.name, ag.name, ir_ag.name, 'AI Agent') AS agent_name,
                    CASE WHEN u.id IS NOT NULL THEN 'operator' ELSE 'ai' END AS agent_type,
                    COUNT(a.id) AS calls_count,
                    COALESCE(SUM(a.duration_sec), 0) / 60 AS total_minutes,
                    ROUND(COUNT(a.id) FILTER (WHERE a.disposition IN ('COMPLETED', 'PROMISE_TO_PAY'))::numeric * 100 / NULLIF(COUNT(a.id), 0), 1) AS success_rate
                """ + DASHBOARD_AGENT_JOINS + DASHBOARD_OPERATOR_JOIN + DASHBOARD_PERIOD_WHERE);
        appendDashboardScope(sql, campaignId, scenarioId);
        sql.append(" GROUP BY agent_id, agent_name, agent_type ORDER BY calls_count DESC LIMIT :limit");
        Query query = dashboardQuery(sql.toString(), companyId, from, to, campaignId, scenarioId)
                .setParameter("limit", limit);
        return rows(query).stream()
                .map(r -> new DashboardAgentRow(
                        asLong(r[0]),
                        (String) r[1],
                        (String) r[2],
                        asLong(r[3]),
                        asLong(r[4]),
                        asDoubleOrNull(r[5]) != null ? asDoubleOrNull(r[5]) : 0.0))
                .toList();
    }

    @Override
    public List<DashboardCampaignRow> dashboardActiveCampaigns(long companyId, int limit) {
        String sql = """
                SELECT c.id, c.name,
                       COUNT(t.id) AS total_targets,
                       COUNT(t.id) FILTER (WHERE t.status IN ('DONE', 'FAILED', 'EXHAUSTED')) AS done_targets
                FROM campaign c
                LEFT JOIN campaign_target t ON t.campaign_id = c.id AND t.phone NOT IN ('MANUAL', 'INBOUND')
                WHERE c.company_id = :companyId
                  AND c.name NOT IN ('MANUAL', 'INBOUND')
                GROUP BY c.id, c.name
                ORDER BY c.id DESC
                LIMIT :limit
                """;
        Query query = em.createNativeQuery(sql)
                .setParameter("companyId", companyId)
                .setParameter("limit", limit);
        return rows(query).stream()
                .map(r -> new DashboardCampaignRow(
                        asLong(r[0]),
                        (String) r[1],
                        asLong(r[2]),
                        asLong(r[3])))
                .toList();
    }

    @Override
    public List<DashboardLiveRow> dashboardLiveCalls(long companyId, int limit) {
        String sql = """
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
                """;
        Query query = em.createNativeQuery(sql)
                .setParameter("companyId", companyId)
                .setParameter("limit", limit);
        return rows(query).stream()
                .map(r -> new DashboardLiveRow(
                        asLong(r[0]),
                        (String) r[1],
                        (String) r[2],
                        (String) r[3],
                        asInstant(r[4]),
                        (String) r[5]))
                .toList();
    }

    @Override
    public List<DashboardRecentCallRow> dashboardRecentCalls(long companyId, Instant from, Instant to,
                                                             Long campaignId, Long scenarioId, int limit) {
        StringBuilder sql = new StringBuilder("""
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
                """ + DASHBOARD_AGENT_JOINS + DASHBOARD_OPERATOR_JOIN + DASHBOARD_PERIOD_WHERE);
        appendDashboardScope(sql, campaignId, scenarioId);
        sql.append(" ORDER BY a.started_at DESC LIMIT :limit");
        Query query = dashboardQuery(sql.toString(), companyId, from, to, campaignId, scenarioId)
                .setParameter("limit", limit);
        return rows(query).stream()
                .map(r -> new DashboardRecentCallRow(
                        asLong(r[0]),
                        (String) r[1],
                        (String) r[2],
                        (String) r[3],
                        (String) r[4],
                        (String) r[5],
                        (String) r[6],
                        asInt(r[7]),
                        asInstant(r[8]),
                        Boolean.TRUE.equals(r[9])))
                .toList();
    }

    private Map<String, Long> countBy(String sql, long campaignId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : rows(em.createNativeQuery(sql).setParameter("campaignId", campaignId))) {
            counts.put(String.valueOf(row[0]), asLong(row[1]));
        }
        return counts;
    }

    @SuppressWarnings("unchecked")
    private static List<Object[]> rows(Query query) {
        return query.getResultList();
    }

    private static long asLong(Object o) {
        return ((Number) o).longValue();
    }

    private static int asInt(Object o) {
        return ((Number) o).intValue();
    }

    private static Float asFloatOrNull(Object o) {
        return o == null ? null : ((Number) o).floatValue();
    }

    private static Double asDoubleOrNull(Object o) {
        return o == null ? null : ((Number) o).doubleValue();
    }

    private static <E extends Enum<E>> E asEnumOrNull(Class<E> type, Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, (String) o);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Instant asInstant(Object o) {
        if (o instanceof Instant i) {
            return i;
        }
        if (o instanceof java.sql.Timestamp t) {
            return t.toInstant();
        }
        if (o instanceof java.util.Date d) {
            return d.toInstant();
        }
        throw new IllegalStateException("Unexpected temporal value: " + o.getClass());
    }

    private static Instant asInstantOrNull(Object o) {
        return o == null ? null : asInstant(o);
    }

    private static LocalDate asLocalDateOrNull(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof LocalDate d) {
            return d;
        }
        if (o instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        throw new IllegalStateException("Unexpected date value: " + o.getClass());
    }

    private static CallRow toCallRow(Object[] r) {
        return new CallRow(
                asLong(r[0]),
                asLong(r[1]),
                (String) r[2],
                (String) r[3],
                asInstantOrNull(r[4]),
                asInstantOrNull(r[5]),
                r[6] == null ? null : asInt(r[6]),
                asEnumOrNull(Disposition.class, r[7]),
                (String) r[8],
                r[9] != null,
                (String) r[10],
                asLocalDateOrNull(r[11]),
                (BigDecimal) r[12],
                r[13] == null ? null : asLong(r[13]),
                (String) r[14],
                (String) r[15],
                (String) r[16]);
    }
}
