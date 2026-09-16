package uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.report.application.port.output.CallReportRepository;
import uz.murodjon.robotcallv2.report.domain.entity.CallFilter;
import uz.murodjon.robotcallv2.report.domain.entity.CallReport;
import uz.murodjon.robotcallv2.report.domain.entity.CallTechnicalMetrics;
import uz.murodjon.robotcallv2.report.domain.entity.PhoneCallHistoryEntry;
import uz.murodjon.robotcallv2.report.domain.entity.ReportedCall;
import uz.murodjon.robotcallv2.report.domain.entity.TranscriptLine;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.dialog.ReasonCode;
import uz.murodjon.robotcallv2.shared.dialog.Sentiment;
import uz.murodjon.robotcallv2.shared.util.LikePatterns;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter.ReportSql.doubleOrNull;
import static uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter.ReportSql.enumOrNull;
import static uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter.ReportSql.firstOrNull;
import static uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter.ReportSql.floatOrNull;
import static uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter.ReportSql.instantOrNull;
import static uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter.ReportSql.intOrNull;
import static uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter.ReportSql.localDateOrNull;
import static uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter.ReportSql.longOrNull;
import static uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter.ReportSql.toTimestamptz;

/**
 * The call list, one call in full, and a number's history. Native SQL through
 * {@link JdbcClient}, every column read by its alias.
 */
@Component
public class CallReportRepositoryAdapter implements CallReportRepository {

    /** One call as a report line: the attempt, its target and campaign, its result and operator. */
    private static final String CALL_SELECT = """
            SELECT a.id            AS call_id,
                   a.target_id     AS target_id,
                   -- The attempt's own number since V4; campaign_target is the fallback
                   -- for rows written before it. A manual or inbound call has no target.
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

    /** What the list's total is counted over: the only join its filter reaches. */
    private static final String CALL_COUNT = """
            SELECT count(*) AS n
            FROM call_attempt a
            LEFT JOIN campaign_target t ON t.id = a.target_id
            """;

    private final JdbcClient jdbc;

    public CallReportRepositoryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    private record CallExtras(String reasonCode, String sentiment, boolean needsFollowUp, String followUpNote,
                              boolean escalated, String errorMessage) {

        static final CallExtras NONE = new CallExtras(null, null, false, null, false, null);
    }

    @Override
    public List<ReportedCall> findCalls(long companyId, Long campaignId, CallFilter filter) {
        return buildCallPage(companyId, campaignId, filter).prepare(jdbc)
                .query(CallReportRepositoryAdapter::toReportedCall)
                .list();
    }

    @Override
    public long countCalls(long companyId, Long campaignId, CallFilter filter) {
        return applyCallFilter(new SqlStatement(CALL_COUNT), companyId, campaignId, filter).prepare(jdbc)
                .query((rs, n) -> rs.getLong("n"))
                .single();
    }

    @Override
    public List<ReportedCall> findCallsForExport(long companyId, CallFilter filter, int limit) {
        return applyCallFilter(new SqlStatement(CALL_SELECT), companyId, null, filter)
                .append(filter.orderByClause())
                .append("LIMIT :limit")
                .bind("limit", limit)
                .prepare(jdbc)
                .query(CallReportRepositoryAdapter::toReportedCall)
                .list();
    }

    /**
     * One page of the list. By page number it is the filter's order with LIMIT/OFFSET. After
     * a cursor it is the calls below that id, newest first, with no OFFSET: the rows before
     * the page are never read, however deep the page is.
     */
    static SqlStatement buildCallPage(long companyId, Long campaignId, CallFilter filter) {
        SqlStatement statement = applyCallFilter(new SqlStatement(CALL_SELECT), companyId, campaignId, filter);
        if (filter.hasCursor()) {
            return statement.where("a.id < :afterId", "afterId", filter.afterId())
                    .append("ORDER BY a.id DESC LIMIT :limit")
                    .bind("limit", filter.sizeOrDefault());
        }
        return statement.append(filter.orderByClause())
                .append("LIMIT :limit OFFSET :offset")
                .bind("limit", filter.sizeOrDefault())
                .bind("offset", filter.offset());
    }

    /**
     * The list's WHERE: the company, the campaign the list is opened on (if any), then the
     * filter's own narrowing — written once, so the page, its count and the export always
     * read the same calls. Explicit {@code ids} replace the filter's narrowing (that is how a
     * selection is exported), never the company or the campaign.
     */
    static SqlStatement applyCallFilter(SqlStatement statement, long companyId, Long campaignId, CallFilter filter) {
        statement.where("a.company_id = :companyId", "companyId", companyId);
        if (campaignId != null) {
            statement.where("t.campaign_id = :scopeCampaignId", "scopeCampaignId", campaignId);
        }
        if (filter.ids() != null && !filter.ids().isEmpty()) {
            return statement.where("a.id IN (:ids)", "ids", filter.ids());
        }
        String phonePattern = LikePatterns.buildContainsPattern(filter.q());
        if (phonePattern != null) {
            statement.where("COALESCE(a.phone, t.phone) LIKE :q ESCAPE '!'", "q", phonePattern);
        }
        if (filter.campaignId() != null) {
            statement.where("t.campaign_id = :campaignId", "campaignId", filter.campaignId());
        }
        if (filter.disposition() != null) {
            statement.where("a.disposition = :disposition", "disposition", filter.disposition().name());
        }
        if (filter.dateFrom() != null) {
            statement.where("a.started_at >= :dateFrom", "dateFrom", toTimestamptz(filter.dateFrom()));
        }
        if (filter.dateTo() != null) {
            statement.where("a.started_at < :dateTo", "dateTo", toTimestamptz(filter.dateTo()));
        }
        if (filter.durationMinSec() != null) {
            statement.where("a.duration_sec >= :durationMinSec", "durationMinSec", filter.durationMinSec());
        }
        if (filter.durationMaxSec() != null) {
            statement.where("a.duration_sec <= :durationMaxSec", "durationMaxSec", filter.durationMaxSec());
        }
        return statement;
    }

    @Override
    public ReportedCall findCall(long companyId, long callId) {
        return jdbc.sql(CALL_SELECT + " WHERE a.id = :callId AND a.company_id = :companyId")
                .param("callId", callId)
                .param("companyId", companyId)
                .query(CallReportRepositoryAdapter::toReportedCall)
                .optional()
                .orElse(null);
    }

    /** One read-only transaction for the call and the three reads that complete it. */
    @Override
    @Transactional(readOnly = true)
    public CallReport findCallReport(long companyId, long callId) {
        ReportedCall call = findCall(companyId, callId);
        if (call == null) {
            return null;
        }

        List<TranscriptLine> transcript = jdbc.sql(
                        "SELECT seq, role, text, dialog_state, ts_offset_ms, stt_confidence "
                                + "FROM call_transcript WHERE call_id = :callId ORDER BY seq")
                .param("callId", callId)
                .query((rs, n) -> new TranscriptLine(
                        rs.getInt("seq"), rs.getString("role"), rs.getString("text"), rs.getString("dialog_state"),
                        rs.getInt("ts_offset_ms"), floatOrNull(rs, "stt_confidence")))
                .list();

        CallExtras extras = jdbc.sql(
                        "SELECT r.reason_code, r.sentiment, r.needs_follow_up, r.follow_up_note, r.escalated, "
                                + "a.error_message "
                                + "FROM call_attempt a LEFT JOIN call_result r ON r.call_id = a.id WHERE a.id = :callId")
                .param("callId", callId)
                .query((rs, n) -> new CallExtras(rs.getString("reason_code"), rs.getString("sentiment"),
                        rs.getBoolean("needs_follow_up"), rs.getString("follow_up_note"),
                        rs.getBoolean("escalated"), rs.getString("error_message")))
                .optional()
                .orElse(CallExtras.NONE);
        return new CallReport(
                call,
                transcript,
                enumOrNull(ReasonCode.class, extras.reasonCode()),
                enumOrNull(Sentiment.class, extras.sentiment()),
                extras.needsFollowUp(),
                extras.followUpNote(),
                extras.escalated(),
                extras.errorMessage(),
                findTechnicalMetrics(callId));
    }

    private CallTechnicalMetrics findTechnicalMetrics(long callId) {
        return jdbc.sql("SELECT channel_name, trunk, amd_result, stt_provider, tts_provider, tts_voice, "
                        + "llm_model, prompt_tokens, completion_tokens, cached_tokens, turn_count, "
                        + "avg_turn_latency_ms, max_turn_latency_ms, avg_llm_latency_ms, max_llm_latency_ms, "
                        + "rtp_packets_received, rtp_packets_lost, rtp_jitter_ms, rtp_mos "
                        + "FROM call_technical WHERE call_id = :callId")
                .param("callId", callId)
                .query((rs, n) -> new CallTechnicalMetrics(
                        rs.getString("channel_name"), rs.getString("trunk"), rs.getString("amd_result"),
                        rs.getString("stt_provider"), rs.getString("tts_provider"), rs.getString("tts_voice"),
                        rs.getString("llm_model"),
                        intOrNull(rs, "prompt_tokens"),
                        intOrNull(rs, "completion_tokens"),
                        intOrNull(rs, "cached_tokens"),
                        intOrNull(rs, "turn_count"),
                        intOrNull(rs, "avg_turn_latency_ms"),
                        intOrNull(rs, "max_turn_latency_ms"),
                        intOrNull(rs, "avg_llm_latency_ms"),
                        intOrNull(rs, "max_llm_latency_ms"),
                        intOrNull(rs, "rtp_packets_received"),
                        intOrNull(rs, "rtp_packets_lost"),
                        intOrNull(rs, "rtp_jitter_ms"),
                        doubleOrNull(rs, "rtp_mos")))
                .optional()
                .orElse(null);
    }

    @Override
    public Long findRecordingFileId(long companyId, long callId) {
        return firstOrNull(jdbc.sql(
                        "SELECT recording_file_id FROM call_attempt WHERE id = :callId AND company_id = :companyId")
                .param("callId", callId)
                .param("companyId", companyId)
                .query((rs, n) -> longOrNull(rs, "recording_file_id"))
                .list());
    }

    @Override
    public List<PhoneCallHistoryEntry> findCallsByPhone(long companyId, String phone, int limit) {
        return jdbc.sql("SELECT a.id AS call_id, cmp.name AS campaign_name, a.started_at AS started_at, "
                        + "a.duration_sec AS duration_sec, a.disposition AS disposition "
                        + "FROM call_attempt a "
                        + "LEFT JOIN campaign_target t ON t.id = a.target_id "
                        + "LEFT JOIN campaign cmp ON cmp.id = t.campaign_id "
                        + "WHERE COALESCE(a.phone, t.phone) = :phone AND a.company_id = :companyId "
                        + "ORDER BY a.started_at DESC NULLS LAST LIMIT :limit")
                .param("phone", phone)
                .param("companyId", companyId)
                .param("limit", limit)
                .query((rs, n) -> new PhoneCallHistoryEntry(
                        rs.getLong("call_id"), rs.getString("campaign_name"), instantOrNull(rs, "started_at"),
                        intOrNull(rs, "duration_sec"), rs.getString("disposition")))
                .list();
    }

    private static ReportedCall toReportedCall(ResultSet rs, int rowNum) throws SQLException {
        return new ReportedCall(
                rs.getLong("call_id"),
                rs.getLong("target_id"),
                rs.getString("phone"),
                rs.getString("language"),
                instantOrNull(rs, "started_at"),
                instantOrNull(rs, "ended_at"),
                intOrNull(rs, "duration_sec"),
                enumOrNull(Disposition.class, rs.getString("disposition")),
                rs.getString("hangup_cause"),
                rs.getObject("recording_file_id") != null,
                rs.getString("summary"),
                localDateOrNull(rs, "promised_date"),
                rs.getBigDecimal("promised_amount"),
                longOrNull(rs, "crm_note_id"),
                rs.getString("client_name"),
                rs.getString("campaign_name"),
                rs.getString("operator_name"));
    }
}
