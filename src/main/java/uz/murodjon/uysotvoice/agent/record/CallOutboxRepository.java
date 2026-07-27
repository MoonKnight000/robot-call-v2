package uz.murodjon.uysotvoice.agent.record;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import uz.murodjon.uysotvoice.agent.dialog.CallSummary;
import uz.murodjon.uysotvoice.shared.dialog.ReasonCode;
import uz.murodjon.uysotvoice.shared.dialog.Sentiment;

import java.sql.Date;
import java.util.List;

/**
 * Finds post-call work that did not complete the first time (see {@link CallOutboxService}).
 *
 * <p>Two queues, both derived from the data rather than kept in a separate table: a result
 * with no {@code crm_note_id} is a note that never posted, and an ended attempt with
 * transcripts but no result row is a summary that never generated. Deriving them means
 * there is no outbox table to fall out of sync with reality, and a row cannot be "in the
 * outbox" while the work is actually done.
 */
@Repository
public class CallOutboxRepository {

    private static final Logger log = LoggerFactory.getLogger(CallOutboxRepository.class);

    /** A written result whose CRM note has not been accepted yet. */
    public record PendingNote(long callId, long clientId, CallSummary summary) {
    }

    /** A finished call with a transcript but no result row — the summary failed. */
    public record PendingSummary(long callId, long clientId) {
    }

    private static final RowMapper<PendingNote> NOTE_MAPPER = (rs, i) -> new PendingNote(
            rs.getLong("call_id"),
            rs.getLong("client_id"),
            new CallSummary(
                    rs.getString("summary"),
                    parseEnum(ReasonCode.class, rs.getString("reason_code")),
                    toLocalDate(rs.getDate("promised_date")),
                    rs.getBigDecimal("promised_amount"),
                    parseEnum(Sentiment.class, rs.getString("sentiment")),
                    rs.getBoolean("needs_follow_up"),
                    rs.getString("follow_up_note")));

    private static final RowMapper<PendingSummary> SUMMARY_MAPPER = (rs, i) ->
            new PendingSummary(rs.getLong("call_id"), rs.getLong("client_id"));

    private final JdbcTemplate jdbc;

    public CallOutboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Results whose note still has to reach the CRM. The summary is rebuilt from the
     * stored columns, so a retry costs an HTTP call and not another LLM call.
     */
    public List<PendingNote> notesAwaitingCrm(int maxAttempts, int limit) {
        try {
            return jdbc.query(
                    "SELECT r.call_id, t.client_id, r.summary, r.reason_code, r.promised_date, "
                            + "r.promised_amount, r.sentiment, r.needs_follow_up, r.follow_up_note "
                            + "FROM call_result r "
                            + "JOIN call_attempt a ON a.id = r.call_id "
                            + "JOIN campaign_target t ON t.id = a.target_id "
                            + "WHERE r.crm_note_id IS NULL AND r.crm_attempts < ? "
                            + "ORDER BY r.id LIMIT ?",
                    NOTE_MAPPER, maxAttempts, limit);
        } catch (Exception e) {
            log.warn("Outbox scan for CRM notes failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Finished calls that still have no {@code call_result}. Restricted to attempts with
     * transcripts: a call nobody spoke on has nothing to summarize, and would otherwise be
     * retried until its attempt counter ran out.
     */
    public List<PendingSummary> attemptsAwaitingSummary(int maxAttempts, int limit) {
        try {
            return jdbc.query(
                    "SELECT a.id AS call_id, t.client_id "
                            + "FROM call_attempt a "
                            + "JOIN campaign_target t ON t.id = a.target_id "
                            + "LEFT JOIN call_result r ON r.call_id = a.id "
                            + "WHERE a.ended_at IS NOT NULL AND r.call_id IS NULL "
                            + "AND a.finalize_attempts < ? "
                            + "AND EXISTS (SELECT 1 FROM call_transcript c WHERE c.call_id = a.id) "
                            + "ORDER BY a.id LIMIT ?",
                    SUMMARY_MAPPER, maxAttempts, limit);
        } catch (Exception e) {
            log.warn("Outbox scan for summaries failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** The note landed — record its id so the row leaves the queue. */
    public void markCrmPosted(long callId, Long noteId) {
        try {
            jdbc.update("UPDATE call_result SET crm_note_id = ?, crm_attempts = crm_attempts + 1, "
                    + "crm_last_error = NULL WHERE call_id = ?", noteId, callId);
        } catch (Exception e) {
            log.warn("markCrmPosted failed for call {}: {}", callId, e.getMessage());
        }
    }

    /** The post failed — count the attempt so a permanently broken row stops retrying. */
    public void markCrmFailed(long callId, String error) {
        try {
            jdbc.update("UPDATE call_result SET crm_attempts = crm_attempts + 1, crm_last_error = ? "
                    + "WHERE call_id = ?", error, callId);
        } catch (Exception e) {
            log.warn("markCrmFailed failed for call {}: {}", callId, e.getMessage());
        }
    }

    /** Count a summary attempt, whether or not it produced anything. */
    public void countSummaryAttempt(long callId) {
        try {
            jdbc.update("UPDATE call_attempt SET finalize_attempts = finalize_attempts + 1 WHERE id = ?",
                    callId);
        } catch (Exception e) {
            log.warn("countSummaryAttempt failed for call {}: {}", callId, e.getMessage());
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            // A value written by an older build of the enum. Losing one field is better
            // than losing the note it belongs to.
            log.debug("Unknown {} value '{}' in call_result", type.getSimpleName(), value);
            return null;
        }
    }

    private static java.time.LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }
}
