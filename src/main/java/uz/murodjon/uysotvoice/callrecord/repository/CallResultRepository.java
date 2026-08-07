package uz.murodjon.uysotvoice.callrecord.repository;

import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/** JPA-backed DAO for {@code call_result} (§4.3). */
@Repository
public class CallResultRepository {

    private final CallResultJpaRepository jpa;

    public CallResultRepository(CallResultJpaRepository jpa) {
        this.jpa = jpa;
    }

    /**
     * Idempotent: the outbox re-runs the summary for calls that have no result yet, and
     * two instances sweeping at once would otherwise have one of them fail on the unique
     * {@code call_id}. Whichever writes first wins — they are summarizing the same
     * transcript.
     */
    public void insertIgnoringConflict(long callId, String summary, String reasonCode, LocalDate promisedDate,
                                       BigDecimal promisedAmount, String sentiment, boolean needsFollowUp,
                                       String followUpNote, boolean escalated, Long crmNoteId, String outcome) {
        jpa.insertIgnoringConflict(callId, summary, reasonCode, promisedDate, promisedAmount, sentiment,
                needsFollowUp, followUpNote, escalated, crmNoteId, outcome);
    }
}
