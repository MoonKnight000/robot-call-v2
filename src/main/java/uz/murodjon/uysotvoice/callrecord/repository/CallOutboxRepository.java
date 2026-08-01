package uz.murodjon.uysotvoice.callrecord.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.agent.dialog.CallSummary;
import uz.murodjon.uysotvoice.callrecord.dto.PendingNote;
import uz.murodjon.uysotvoice.callrecord.dto.PendingSummary;
import uz.murodjon.uysotvoice.callrecord.entity.CallResult;
import uz.murodjon.uysotvoice.callrecord.service.CallOutboxService;

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

    private final CallResultJpaRepository results;
    private final CallAttemptJpaRepository callAttempts;

    public CallOutboxRepository(CallResultJpaRepository results, CallAttemptJpaRepository callAttempts) {
        this.results = results;
        this.callAttempts = callAttempts;
    }

    /**
     * Results whose note still has to reach the CRM. The summary is rebuilt from the
     * stored columns, so a retry costs an HTTP call and not another LLM call.
     */
    public List<PendingNote> notesAwaitingCrm(int maxAttempts, int limit) {
        try {
            return results.notesAwaitingCrm(maxAttempts, PageRequest.of(0, limit)).stream()
                    .map(row -> {
                        CallResult r = (CallResult) row[0];
                        long clientId = (Long) row[1];
                        return new PendingNote(r.getCall().getId(), clientId, new CallSummary(
                                r.getSummary(),
                                r.getReasonCode(),
                                r.getPromisedDate(),
                                r.getPromisedAmount(),
                                r.getSentiment(),
                                r.isNeedsFollowUp(),
                                r.getFollowUpNote()));
                    })
                    .toList();
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
            return callAttempts.attemptsAwaitingSummary(maxAttempts, PageRequest.of(0, limit)).stream()
                    .map(row -> new PendingSummary((Long) row[0], (Long) row[1]))
                    .toList();
        } catch (Exception e) {
            log.warn("Outbox scan for summaries failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** The note landed — record its id so the row leaves the queue. */
    public void markCrmPosted(long callId, Long noteId) {
        try {
            results.markCrmPosted(callId, noteId);
        } catch (Exception e) {
            log.warn("markCrmPosted failed for call {}: {}", callId, e.getMessage());
        }
    }

    /** The post failed — count the attempt so a permanently broken row stops retrying. */
    public void markCrmFailed(long callId, String error) {
        try {
            results.markCrmFailed(callId, error);
        } catch (Exception e) {
            log.warn("markCrmFailed failed for call {}: {}", callId, e.getMessage());
        }
    }

    /** Count a summary attempt, whether or not it produced anything. */
    public void countSummaryAttempt(long callId) {
        try {
            callAttempts.countSummaryAttempt(callId);
        } catch (Exception e) {
            log.warn("countSummaryAttempt failed for call {}: {}", callId, e.getMessage());
        }
    }
}
