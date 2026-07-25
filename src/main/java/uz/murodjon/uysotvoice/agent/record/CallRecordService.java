package uz.murodjon.uysotvoice.agent.record;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import uz.murodjon.uysotvoice.agent.dialog.CallSummary;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Persists the call lifecycle to Postgres (PROJECT.md §6, Stage 9): a
 * {@code call_attempt} per call, {@code call_transcript} per utterance, and one
 * {@code call_result}. Uses {@link JdbcTemplate} against the known Flyway schema —
 * no JPA entities, so nothing to keep in sync with {@code ddl-auto: validate}.
 *
 * <p>All writes are best-effort: a DB error is logged and swallowed so it never
 * breaks the live call. {@link #startAttempt} returns {@code 0} on failure, which
 * downstream treats as "no record".
 */
@Service
public class CallRecordService {

    private static final Logger log = LoggerFactory.getLogger(CallRecordService.class);

    private final JdbcTemplate jdbc;
    private final AtomicLong manualTargetId = new AtomicLong(0);
    private final Map<Long, AtomicInteger> seqCounters = new ConcurrentHashMap<>();

    public CallRecordService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Id of the placeholder target seeded by {@code V2} (phone = 'MANUAL'); cached. */
    public long manualTargetId() {
        long cached = manualTargetId.get();
        if (cached != 0) {
            return cached;
        }
        try {
            Long id = jdbc.queryForObject(
                    "SELECT id FROM campaign_target WHERE phone = 'MANUAL' ORDER BY id LIMIT 1", Long.class);
            if (id != null) {
                manualTargetId.set(id);
                return id;
            }
        } catch (Exception e) {
            log.warn("Manual target lookup failed: {}", e.getMessage());
        }
        return 0;
    }

    /** Insert a call_attempt (answered now); returns its id, or 0 on failure. */
    public long startAttempt(long targetId, String channelId, String language) {
        if (targetId == 0) {
            return 0;
        }
        try {
            Timestamp now = Timestamp.from(Instant.now());
            KeyHolder key = new GeneratedKeyHolder();
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO call_attempt(target_id, asterisk_channel, language, started_at, answered_at) "
                                + "VALUES (?, ?, ?, ?, ?)", new String[]{"id"});
                ps.setLong(1, targetId);
                ps.setString(2, channelId);
                ps.setString(3, language);
                ps.setTimestamp(4, now);
                ps.setTimestamp(5, now);
                return ps;
            }, key);
            Number id = key.getKey();
            return id != null ? id.longValue() : 0;
        } catch (Exception e) {
            log.warn("startAttempt failed for {}: {}", channelId, e.getMessage());
            return 0;
        }
    }

    /** Insert one transcript line; seq is auto-assigned per call. */
    public void addTranscript(long callId, String role, String text, String dialogState,
                              int tsOffsetMs, Float confidence) {
        if (callId == 0 || text == null || text.isBlank()) {
            return;
        }
        int seq = seqCounters.computeIfAbsent(callId, k -> new AtomicInteger()).incrementAndGet();
        try {
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO call_transcript(call_id, seq, role, text, dialog_state, ts_offset_ms, stt_confidence) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)", Statement.NO_GENERATED_KEYS);
                ps.setLong(1, callId);
                ps.setInt(2, seq);
                ps.setString(3, role);
                ps.setString(4, text);
                if (dialogState != null) {
                    ps.setString(5, dialogState);
                } else {
                    ps.setNull(5, Types.VARCHAR);
                }
                ps.setInt(6, Math.max(0, tsOffsetMs));
                if (confidence != null) {
                    ps.setFloat(7, confidence);
                } else {
                    ps.setNull(7, Types.REAL);
                }
                return ps;
            });
        } catch (Exception e) {
            log.warn("addTranscript failed for call {}: {}", callId, e.getMessage());
        }
    }

    /** Full transcript as {@code ROLE: text} lines, in order (for the summary LLM). */
    public String transcriptText(long callId) {
        if (callId == 0) {
            return "";
        }
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT role, text FROM call_transcript WHERE call_id = ? ORDER BY seq", callId);
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> row : rows) {
                sb.append(row.get("role")).append(": ").append(row.get("text")).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("transcriptText failed for call {}: {}", callId, e.getMessage());
            return "";
        }
    }

    /** Close out a call_attempt with its outcome. */
    public void finishAttempt(long callId, Disposition disposition, String recordingUrl, int durationSec) {
        seqCounters.remove(callId);
        if (callId == 0) {
            return;
        }
        try {
            jdbc.update("UPDATE call_attempt SET ended_at = ?, duration_sec = ?, disposition = ?, recording_url = ? "
                            + "WHERE id = ?",
                    Timestamp.from(Instant.now()),
                    durationSec,
                    disposition != null ? disposition.name() : null,
                    recordingUrl,
                    callId);
        } catch (Exception e) {
            log.warn("finishAttempt failed for call {}: {}", callId, e.getMessage());
        }
    }

    /** Insert the single call_result row (§4.3). */
    public void writeResult(long callId, CallSummary s, boolean escalated, Long crmNoteId) {
        if (callId == 0 || s == null) {
            return;
        }
        try {
            jdbc.update("INSERT INTO call_result(call_id, summary, reason_code, promised_date, promised_amount, "
                            + "sentiment, needs_follow_up, follow_up_note, escalated, crm_note_id) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    callId,
                    s.summary() != null ? s.summary() : "",
                    s.reasonCode() != null ? s.reasonCode().name() : null,
                    s.promisedDate() != null ? java.sql.Date.valueOf(s.promisedDate()) : null,
                    s.promisedAmount(),
                    s.sentiment() != null ? s.sentiment().name() : null,
                    s.needsFollowUp(),
                    s.followUpNote(),
                    escalated,
                    crmNoteId);
            log.info("call_result written for call {} (disposition-escalated={})", callId, escalated);
        } catch (Exception e) {
            log.warn("writeResult failed for call {}: {}", callId, e.getMessage());
        }
    }
}
