package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallResultEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Spring Data repository for {@link CallResultEntity}. */
@Repository
public interface CallResultJpaRepository extends JpaRepository<CallResultEntity, Long> {

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
                                @Param("escalated") boolean escalated, @Param("crmNoteId") String crmNoteId,
                                @Param("outcome") String outcome);

    /** The note landed — record the id the CRM gave it (the outbox_message row holds the delivery state). */
    @Modifying @Transactional
    @Query("UPDATE CallResultEntity r SET r.crmNoteId = :noteId, r.crmAttempts = r.crmAttempts + 1, "
            + "r.crmLastError = NULL WHERE r.call.id = :callId")
    void markCrmPosted(@Param("callId") long callId, @Param("noteId") String noteId);

    /** At most {@code batchSize} results, across every company, written before {@code cutoff}. */
    @Modifying @Transactional
    @Query(value = "DELETE FROM call_result WHERE id IN "
            + "(SELECT id FROM call_result WHERE created_at < :cutoff LIMIT :batchSize)", nativeQuery = true)
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
