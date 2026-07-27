package uz.murodjon.uysotvoice.report;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only queries behind the reporting API (PROJECT.md §10 Bosqich 12).
 *
 * <p>Kept apart from the write-path DAOs: these are reporting joins that may run over a
 * whole campaign's history, and mixing them into {@link
 * uz.murodjon.uysotvoice.agent.record.CallRecordService} — whose every method is on a live
 * call's path and swallows its own errors — would blur which queries are allowed to be slow
 * and which must never fail silently. Here a broken query should surface as a 500.
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

    private static final RowMapper<CallRow> CALL_MAPPER = (rs, i) -> new CallRow(
            rs.getLong("call_id"),
            rs.getLong("target_id"),
            rs.getString("phone"),
            rs.getString("language"),
            instant(rs, "started_at"),
            instant(rs, "ended_at"),
            nullableInt(rs, "duration_sec"),
            rs.getString("disposition"),
            rs.getString("hangup_cause"),
            rs.getString("recording_url") != null,
            rs.getString("summary"),
            rs.getDate("promised_date") == null ? null : rs.getDate("promised_date").toLocalDate(),
            rs.getBigDecimal("promised_amount"),
            nullableLong(rs, "crm_note_id"));

    private static final RowMapper<CallDetail.TranscriptLine> TRANSCRIPT_MAPPER = (rs, i) ->
            new CallDetail.TranscriptLine(
                    rs.getInt("seq"),
                    rs.getString("role"),
                    rs.getString("text"),
                    rs.getString("dialog_state"),
                    rs.getInt("ts_offset_ms"),
                    nullableFloat(rs, "stt_confidence"));

    private final JdbcTemplate jdbc;

    public ReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Aggregate outcome for one campaign, or {@code null} if it does not exist. */
    public CampaignStats campaignStats(long campaignId) {
        List<Map<String, Object>> campaign = jdbc.queryForList(
                "SELECT name, status FROM campaign WHERE id = ?", campaignId);
        if (campaign.isEmpty()) {
            return null;
        }

        Map<String, Long> targets = countBy(
                "SELECT status AS k, count(*) AS n FROM campaign_target WHERE campaign_id = ? GROUP BY status",
                campaignId);
        Map<String, Long> dispositions = countBy(
                "SELECT coalesce(a.disposition, 'UNKNOWN') AS k, count(*) AS n "
                        + "FROM call_attempt a JOIN campaign_target t ON t.id = a.target_id "
                        + "WHERE t.campaign_id = ? AND a.ended_at IS NOT NULL "
                        + "GROUP BY coalesce(a.disposition, 'UNKNOWN')",
                campaignId);

        long finished = dispositions.values().stream().mapToLong(Long::longValue).sum();
        long promises = dispositions.getOrDefault(Disposition.PROMISE_TO_PAY.name(), 0L);
        long escalated = dispositions.getOrDefault(Disposition.TRANSFERRED.name(), 0L);

        Double avgDuration = jdbc.queryForObject(
                "SELECT avg(a.duration_sec) FROM call_attempt a JOIN campaign_target t ON t.id = a.target_id "
                        + "WHERE t.campaign_id = ? AND a.duration_sec IS NOT NULL",
                Double.class, campaignId);

        return new CampaignStats(
                campaignId,
                (String) campaign.get(0).get("name"),
                (String) campaign.get(0).get("status"),
                targets,
                dispositions,
                finished,
                finished == 0 ? 0d : (double) promises / finished,
                avgDuration,
                escalated);
    }

    /** Most recent calls of a campaign first. */
    public List<CallRow> callsOfCampaign(long campaignId, int limit, int offset) {
        return jdbc.query(CALL_SELECT + " WHERE t.campaign_id = ? ORDER BY a.id DESC LIMIT ? OFFSET ?",
                CALL_MAPPER, campaignId, limit, offset);
    }

    /** Most recent calls across every campaign. */
    public List<CallRow> recentCalls(int limit, int offset) {
        return jdbc.query(CALL_SELECT + " ORDER BY a.id DESC LIMIT ? OFFSET ?",
                CALL_MAPPER, limit, offset);
    }

    /** One call with its transcript, or {@code null} if there is no such attempt. */
    public CallDetail callDetail(long callId) {
        List<CallRow> rows = jdbc.query(CALL_SELECT + " WHERE a.id = ?", CALL_MAPPER, callId);
        if (rows.isEmpty()) {
            return null;
        }
        List<CallDetail.TranscriptLine> transcript = jdbc.query(
                "SELECT seq, role, text, dialog_state, ts_offset_ms, stt_confidence "
                        + "FROM call_transcript WHERE call_id = ? ORDER BY seq",
                TRANSCRIPT_MAPPER, callId);
        List<Map<String, Object>> extra = jdbc.queryForList(
                "SELECT r.reason_code, r.sentiment, r.needs_follow_up, r.follow_up_note, r.escalated, "
                        + "a.error_message "
                        + "FROM call_attempt a LEFT JOIN call_result r ON r.call_id = a.id WHERE a.id = ?",
                callId);
        Map<String, Object> e = extra.isEmpty() ? Map.of() : extra.get(0);
        return new CallDetail(
                rows.get(0),
                transcript,
                (String) e.get("reason_code"),
                (String) e.get("sentiment"),
                Boolean.TRUE.equals(e.get("needs_follow_up")),
                (String) e.get("follow_up_note"),
                Boolean.TRUE.equals(e.get("escalated")),
                (String) e.get("error_message"));
    }

    /** Where this call's audio lives ({@code file:} path or object-store URL), or null. */
    public String recordingUrl(long callId) {
        List<String> urls = jdbc.queryForList(
                "SELECT recording_url FROM call_attempt WHERE id = ?", String.class, callId);
        return urls.isEmpty() ? null : urls.get(0);
    }

    /** Run a two-column {@code (k, n)} grouping query into an ordered map. */
    private Map<String, Long> countBy(String sql, Object... args) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(sql, args)) {
            counts.put(String.valueOf(row.get("k")), ((Number) row.get("n")).longValue());
        }
        return counts;
    }

    private static java.time.Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Float nullableFloat(ResultSet rs, String column) throws SQLException {
        float value = rs.getFloat(column);
        return rs.wasNull() ? null : value;
    }
}
