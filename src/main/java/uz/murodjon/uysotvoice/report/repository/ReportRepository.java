package uz.murodjon.uysotvoice.report.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.campaign.enums.CampaignStatus;
import uz.murodjon.uysotvoice.company.service.CurrentCompany;
import uz.murodjon.uysotvoice.contact.dto.ContactCallHistoryRow;
import uz.murodjon.uysotvoice.report.dto.CallDetail;
import uz.murodjon.uysotvoice.report.dto.CallFilter;
import uz.murodjon.uysotvoice.report.dto.CallRow;
import uz.murodjon.uysotvoice.report.dto.CallTechnicalDetail;
import uz.murodjon.uysotvoice.report.dto.CampaignStats;
import uz.murodjon.uysotvoice.report.dto.DashboardBucket;
import uz.murodjon.uysotvoice.report.dto.DashboardOutcome;
import uz.murodjon.uysotvoice.report.dto.DashboardRange;
import uz.murodjon.uysotvoice.report.dto.DashboardTotals;
import uz.murodjon.uysotvoice.report.dto.TranscriptLine;
import uz.murodjon.uysotvoice.shared.api.FilterInterface;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;
import uz.murodjon.uysotvoice.shared.dialog.ReasonCode;
import uz.murodjon.uysotvoice.shared.dialog.Sentiment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only queries behind the reporting API (PROJECT.md §10 Bosqich 12).
 *
 * <p>Kept apart from the write-path DAOs: these are reporting joins that may run over a
 * whole campaign's history, and mixing them into {@link
 * uz.murodjon.uysotvoice.callrecord.service.CallRecordService} — whose every method is on a live
 * call's path and swallows its own errors — would blur which queries are allowed to be slow
 * and which must never fail silently. Here a broken query should surface as a 500.
 *
 * <p>These are dynamic, cross-table analytical queries ({@code FILTER}, {@code date_trunc},
 * a WHERE clause assembled per optional filter) that gain nothing from JPQL and would risk
 * subtly different behavior if forced through it, so they run as native SQL through the JPA
 * {@link EntityManager} (still Hibernate, just not {@code JdbcTemplate}) instead of against
 * an {@code @Entity}.
 */
@Repository
public class ReportRepository {

    /**
     * The report line, joined once. {@code call_result} is a LEFT JOIN because a call can
     * legitimately have no result — nobody answered, or the summary is still queued in the
     * outbox — and those calls are exactly the ones a report must not hide.
     */
    private static final String CALL_SELECT = """
            SELECT a.id            AS call_id,
                   a.target_id     AS target_id,
                   t.phone         AS phone,
                   a.language      AS language,
                   a.started_at    AS started_at,
                   a.ended_at      AS ended_at,
                   a.duration_sec  AS duration_sec,
                   a.disposition   AS disposition,
                   a.hangup_cause  AS hangup_cause,
                   a.recording_url AS recording_url,
                   r.summary       AS summary,
                   r.promised_date AS promised_date,
                   r.promised_amount AS promised_amount,
                   r.crm_note_id   AS crm_note_id
            FROM call_attempt a
            JOIN campaign_target t ON t.id = a.target_id
            LEFT JOIN call_result r ON r.call_id = a.id
            """;

    private final EntityManager em;
    private final CurrentCompany company;

    public ReportRepository(EntityManager em, CurrentCompany company) {
        this.em = em;
        this.company = company;
    }

    /**
     * Aggregate outcome for one campaign, or {@code null} if it does not exist (or
     * belongs to another company — a wrong-company id must read exactly like a missing
     * one, not confirm the id exists elsewhere).
     */
    public CampaignStats campaignStats(long campaignId) {
        List<Object[]> campaign = rows(em.createNativeQuery(
                        "SELECT name, status FROM campaign WHERE id = :campaignId AND company_id = :companyId")
                .setParameter("campaignId", campaignId)
                .setParameter("companyId", company.id()));
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

    /** Most recent calls of a campaign first. */
    public List<CallRow> callsOfCampaign(long campaignId, CallFilter filter) {
        List<Object[]> result = rows(em.createNativeQuery(CALL_SELECT
                        + " WHERE t.campaign_id = :campaignId AND a.company_id = :companyId"
                        + filter.orderByClause() + " LIMIT :limit OFFSET :offset")
                .setParameter("campaignId", campaignId)
                .setParameter("companyId", company.id())
                .setParameter("limit", filter.sizeOrDefault())
                .setParameter("offset", filter.offset()));
        return result.stream().map(ReportRepository::toCallRow).toList();
    }

    public long countCallsOfCampaign(long campaignId) {
        Object result = em.createNativeQuery(
                        "SELECT count(*) FROM call_attempt a JOIN campaign_target t ON t.id = a.target_id "
                                + "WHERE t.campaign_id = :campaignId AND a.company_id = :companyId")
                .setParameter("campaignId", campaignId)
                .setParameter("companyId", company.id())
                .getSingleResult();
        return asLong(result);
    }

    /**
     * Most recent calls across every campaign, scoped to the current company, narrowed by
     * whichever optional fields {@code filter} sets (text search, campaign, disposition,
     * date range, duration range) — see {@link #appendCallFilterWhere}.
     */
    public List<CallRow> recentCalls(CallFilter filter) {
        StringBuilder sql = new StringBuilder(CALL_SELECT).append(" WHERE a.company_id = :companyId");
        appendCallFilterWhere(sql, filter);
        sql.append(filter.orderByClause()).append(" LIMIT :limit OFFSET :offset");
        Query query = em.createNativeQuery(sql.toString())
                .setParameter("companyId", company.id())
                .setParameter("limit", filter.sizeOrDefault())
                .setParameter("offset", filter.offset());
        bindCallFilterParams(query, filter);
        return rows(query).stream().map(ReportRepository::toCallRow).toList();
    }

    /** Same scoping/filtering as {@link #recentCalls}, unpaged, for the CSV export. */
    public List<CallRow> exportCalls(CallFilter filter) {
        StringBuilder sql = new StringBuilder(CALL_SELECT).append(" WHERE a.company_id = :companyId");
        appendCallFilterWhere(sql, filter);
        sql.append(filter.orderByClause()).append(" LIMIT :limit");
        Query query = em.createNativeQuery(sql.toString())
                .setParameter("companyId", company.id())
                .setParameter("limit", FilterInterface.MAX_SIZE);
        bindCallFilterParams(query, filter);
        return rows(query).stream().map(ReportRepository::toCallRow).toList();
    }

    public long countRecentCalls(CallFilter filter) {
        StringBuilder sql = new StringBuilder(
                "SELECT count(*) FROM call_attempt a JOIN campaign_target t ON t.id = a.target_id "
                        + "WHERE a.company_id = :companyId");
        appendCallFilterWhere(sql, filter);
        Query query = em.createNativeQuery(sql.toString()).setParameter("companyId", company.id());
        bindCallFilterParams(query, filter);
        return asLong(query.getSingleResult());
    }

    /**
     * Appends the optional {@code CallFilter} fields as {@code AND} clauses, in bind order.
     * {@link CallFilter#ids()}, when set, wins over every other field (a user-selected
     * subset of rows) rather than narrowing an already-filtered view.
     */
    private static void appendCallFilterWhere(StringBuilder sql, CallFilter filter) {
        if (filter.ids() != null && !filter.ids().isEmpty()) {
            sql.append(" AND a.id IN (:ids)");
            return;
        }
        if (filter.q() != null && !filter.q().isBlank()) {
            sql.append(" AND t.phone LIKE :q");
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

    /** One call's report line, or {@code null} if it does not exist (or belongs to another company). */
    public CallRow findCall(long callId) {
        List<Object[]> result = rows(em.createNativeQuery(
                        CALL_SELECT + " WHERE a.id = :callId AND a.company_id = :companyId")
                .setParameter("callId", callId)
                .setParameter("companyId", company.id()));
        return result.isEmpty() ? null : toCallRow(result.get(0));
    }

    /**
     * One call with its transcript, or {@code null} if there is no such attempt (or it
     * belongs to another company). {@code call_transcript}/{@code call_result} are read
     * afterwards keyed by this already-scoped {@code callId}, so neither needs its own
     * company_id column or filter.
     */
    public CallDetail callDetail(long callId) {
        CallRow callRow = findCall(callId);
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

    /** {@code null} for calls that predate {@code call_technical} (§10.5 "Texnik" tab). */
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

    /**
     * Scalar KPI totals over {@code [from, to)} (§10.2 dashboard cards): calls started,
     * how many connected, their average duration, and payment promises. "Answered" is
     * defined as having a measured duration — the call reached a live handset or
     * voicemail rather than ringing out or failing outright.
     */
    public DashboardTotals dashboardTotals(Instant from, Instant to, Long campaignId) {
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
                .setParameter("companyId", company.id())
                .setParameter("from", from)
                .setParameter("to", to);
        if (campaignId != null) {
            query.setParameter("campaignId", campaignId);
        }
        Object[] row = (Object[]) query.getSingleResult();
        return new DashboardTotals(asLong(row[0]), asLong(row[1]), asDoubleOrNull(row[2]), asLong(row[3]));
    }

    /**
     * The same window bucketed by {@code granularity} ({@code hour}/{@code day}/{@code week},
     * see {@link DashboardRange#granularity()}) — the per-metric sparklines on the KPI cards
     * and the "Qo'ng'iroqlar dinamikasi" stacked-area chart both read this one query.
     */
    public List<DashboardBucket> dashboardBuckets(Instant from, Instant to, Long campaignId, String granularity) {
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
                .setParameter("companyId", company.id())
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

    /** Disposition distribution over {@code [from, to)} (§10.2 "Natijalar taqsimoti"). */
    public List<DashboardOutcome> dashboardOutcomes(Instant from, Instant to, Long campaignId) {
        StringBuilder sql = new StringBuilder(
                "SELECT coalesce(a.disposition, 'UNKNOWN') AS disposition, count(*) AS n "
                        + "FROM call_attempt a JOIN campaign_target t ON t.id = a.target_id "
                        + "WHERE a.company_id = :companyId AND a.started_at >= :from AND a.started_at < :to");
        if (campaignId != null) {
            sql.append(" AND t.campaign_id = :campaignId");
        }
        sql.append(" GROUP BY coalesce(a.disposition, 'UNKNOWN') ORDER BY n DESC");
        Query query = em.createNativeQuery(sql.toString())
                .setParameter("companyId", company.id())
                .setParameter("from", from)
                .setParameter("to", to);
        if (campaignId != null) {
            query.setParameter("campaignId", campaignId);
        }
        return rows(query).stream()
                .map(r -> new DashboardOutcome((String) r[0], asLong(r[1])))
                .toList();
    }

    /**
     * A phone's most recent calls, newest first, for a contact's call-history timeline
     * (§10.8 drawer) — matched by phone rather than any {@code contact_id} FK, mirroring
     * the {@code do_not_call_list} precedent of keying opt-outs by phone. A thin
     * projection ({@link uz.murodjon.uysotvoice.contact.dto.ContactCallHistoryRow}), not the
     * full {@link CallRow}, since the drawer only shows a summary line per call.
     */
    public List<ContactCallHistoryRow> callsForPhone(String phone, int limit) {
        List<Object[]> result = rows(em.createNativeQuery(
                        "SELECT a.id AS call_id, cmp.name AS campaign_name, a.started_at AS started_at, "
                                + "a.duration_sec AS duration_sec, a.disposition AS disposition "
                                + "FROM call_attempt a "
                                + "JOIN campaign_target t ON t.id = a.target_id "
                                + "LEFT JOIN campaign cmp ON cmp.id = t.campaign_id "
                                + "WHERE t.phone = :phone AND a.company_id = :companyId "
                                + "ORDER BY a.started_at DESC NULLS LAST LIMIT :limit")
                .setParameter("phone", phone)
                .setParameter("companyId", company.id())
                .setParameter("limit", limit));
        return result.stream()
                .map(r -> new ContactCallHistoryRow(
                        asLong(r[0]), (String) r[1], asInstantOrNull(r[2]),
                        r[3] == null ? null : asInt(r[3]), (String) r[4]))
                .toList();
    }

    /** Where this call's audio lives ({@code file:} path or object-store URL), or null. */
    public String recordingUrl(long callId) {
        List<Object> result = em.createNativeQuery(
                        "SELECT recording_url FROM call_attempt WHERE id = :callId AND company_id = :companyId")
                .setParameter("callId", callId)
                .setParameter("companyId", company.id())
                .getResultList();
        return result.isEmpty() ? null : (String) result.get(0);
    }

    /** Run a two-column {@code (k, n)} grouping query into an ordered map. */
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

    /**
     * A raw column value that does not match any constant of {@code type} (e.g. written by
     * an older build of the enum) reads back as {@code null} rather than failing the whole
     * report row.
     */
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
                r[13] == null ? null : asLong(r[13]));
    }
}
