package uz.murodjon.uysotvoice.callrecord.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.uysotvoice.callrecord.entity.CallResultEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Spring Data repository for {@link CallResultEntity}. */
@Repository
public interface CallResultJpaRepository extends JpaRepository<CallResultEntity, Long> {

    /**
     * Idempotent: the outbox re-runs the summary for calls that have no result yet, and
     * two instances sweeping at once would otherwise have one of them fail on the unique
     * {@code call_id}. Whichever writes first wins — they are summarizing the same
     * transcript.
     */
    @Modifying @Transactional
    @Query(value = "INSERT INTO call_result(call_id, summary, reason_code, promised_date, promised_amount, "
            + "sentiment, needs_follow_up, follow_up_note, escalated, crm_note_id, outcome) "
            + "VALUES (:callId, :summary, :reasonCode, :promisedDate, :promisedAmount, "
            + ":sentiment, :needsFollowUp, :followUpNote, :escalated, :crmNoteId, CAST(:outcome AS jsonb)) "
            + "ON CONFLICT (call_id) DO NOTHING", nativeQuery = true)
    void insertIgnoringConflict(@Param("callId") long callId, @Param("summary") String summary,
                                @Param("reasonCode") String reasonCode, @Param("promisedDate") LocalDate promisedDate,
                                @Param("promisedAmount") BigDecimal promisedAmount, @Param("sentiment") String sentiment,
                                @Param("needsFollowUp") boolean needsFollowUp, @Param("followUpNote") String followUpNote,
                                @Param("escalated") boolean escalated, @Param("crmNoteId") Long crmNoteId,
                                @Param("outcome") String outcome);

    /** The note landed — record its id so the row leaves the queue. */
    @Modifying @Transactional
    @Query("UPDATE CallResultEntity r SET r.crmNoteId = :noteId, r.crmAttempts = r.crmAttempts + 1, "
            + "r.crmLastError = NULL WHERE r.call.id = :callId")
    void markCrmPosted(@Param("callId") long callId, @Param("noteId") Long noteId);

    /** The post failed — count the attempt so a permanently broken row stops retrying. */
    @Modifying @Transactional
    @Query("UPDATE CallResultEntity r SET r.crmAttempts = r.crmAttempts + 1, r.crmLastError = :error "
            + "WHERE r.call.id = :callId")
    void markCrmFailed(@Param("callId") long callId, @Param("error") String error);

    /**
     * Results whose note still has to reach the CRM. Each row is
     * {@code [CallResult, clientId]} — the summary is rebuilt from the entity's stored
     * columns, so a retry costs an HTTP call and not another LLM call.
     */
    @Query("SELECT r, r.call.target.clientId FROM CallResultEntity r "
            + "WHERE r.crmNoteId IS NULL AND r.crmAttempts < :maxAttempts ORDER BY r.id")
    List<Object[]> notesAwaitingCrm(@Param("maxAttempts") int maxAttempts, Pageable limit);
}
