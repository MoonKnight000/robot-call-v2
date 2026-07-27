package uz.murodjon.uysotvoice.agent.record;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.murodjon.uysotvoice.agent.crm.CrmClient;
import uz.murodjon.uysotvoice.agent.dialog.CallSummary;
import uz.murodjon.uysotvoice.agent.summary.SummaryService;

import java.util.List;

/**
 * Retries the post-call work that failed the first time (PROJECT.md §4.3, Stage 9).
 *
 * <p>{@link CallFinalizer} degrades gracefully on purpose — a CRM outage or an LLM quota
 * error must not break call teardown. But "gracefully" meant a WARN log and nothing else:
 * the note was never posted, the summary never generated, and the only record that the
 * call had a result at all was in the transcript table. For a debt-collection call whose
 * whole purpose is a CRM note, losing the note loses the call.
 *
 * <p>So the two failure modes are swept up here. A note that never posted is re-posted
 * from the stored result — no LLM call needed. A summary that never generated is generated
 * again from the transcript, which is still on disk. Both are bounded by an attempt count,
 * so a permanently broken row stops after a few tries instead of retrying forever.
 *
 * <p>Runs on the scheduler thread and blocks on HTTP and the LLM. That is deliberate: this
 * is background repair work, and it must never compete with a live call for the call
 * executor.
 */
@Service
public class CallOutboxService {

    private static final Logger log = LoggerFactory.getLogger(CallOutboxService.class);

    private final CallOutboxRepository outbox;
    private final CallRecordService records;
    private final SummaryService summaryService;
    private final CrmClient crmClient;
    private final boolean enabled;
    private final int maxAttempts;
    private final int batch;

    public CallOutboxService(CallOutboxRepository outbox,
                             CallRecordService records,
                             SummaryService summaryService,
                             CrmClient crmClient,
                             @Value("${voice-agent.outbox.enabled:true}") boolean enabled,
                             @Value("${voice-agent.outbox.max-attempts:5}") int maxAttempts,
                             @Value("${voice-agent.outbox.batch:20}") int batch) {
        this.outbox = outbox;
        this.records = records;
        this.summaryService = summaryService;
        this.crmClient = crmClient;
        this.enabled = enabled;
        this.maxAttempts = maxAttempts;
        this.batch = batch;
    }

    @Scheduled(fixedDelayString = "#{${voice-agent.outbox.interval-minutes:5} * 60 * 1000}",
            initialDelay = 60_000)
    public void sweep() {
        if (!enabled) {
            return;
        }
        retrySummaries();
        retryCrmNotes();
    }

    /**
     * Summarize calls whose first attempt produced nothing. Runs before the note sweep so
     * a result written here can have its note posted in the same pass.
     */
    private void retrySummaries() {
        List<CallOutboxRepository.PendingSummary> pending =
                outbox.attemptsAwaitingSummary(maxAttempts, batch);
        if (pending.isEmpty()) {
            return;
        }
        log.info("Outbox: {} call(s) awaiting a summary", pending.size());
        for (CallOutboxRepository.PendingSummary p : pending) {
            // Counted first: if the LLM call throws in a way that skips the rest of the
            // loop body, the attempt still has to be spent, or a poisonous row is retried
            // on every sweep forever.
            outbox.countSummaryAttempt(p.callId());
            try {
                String transcript = records.transcriptText(p.callId());
                CallSummary summary = summaryService.summarize(transcript);
                if (summary == null) {
                    log.debug("Outbox: summary still unavailable for call {}", p.callId());
                    continue;
                }
                Long noteId = crmClient.postNote(p.clientId(), summary);
                records.writeResult(p.callId(), summary, false, noteId);
                log.info("Outbox: summarized call {} on retry (crmNoteId={})", p.callId(), noteId);
            } catch (Exception e) {
                log.warn("Outbox: summary retry failed for call {}: {}", p.callId(), e.getMessage());
            }
        }
    }

    /** Re-post notes the CRM never accepted, using the already-stored summary. */
    private void retryCrmNotes() {
        if (!crmClient.enabled()) {
            return; // nothing to retry against; rows wait until the CRM is configured
        }
        List<CallOutboxRepository.PendingNote> pending = outbox.notesAwaitingCrm(maxAttempts, batch);
        if (pending.isEmpty()) {
            return;
        }
        log.info("Outbox: {} CRM note(s) to re-post", pending.size());
        for (CallOutboxRepository.PendingNote p : pending) {
            try {
                Long noteId = crmClient.postNote(p.clientId(), p.summary());
                if (noteId != null) {
                    outbox.markCrmPosted(p.callId(), noteId);
                    log.info("Outbox: CRM note posted for call {} (noteId={})", p.callId(), noteId);
                } else {
                    outbox.markCrmFailed(p.callId(), "CRM returned no note id");
                }
            } catch (Exception e) {
                outbox.markCrmFailed(p.callId(), e.getMessage());
                log.warn("Outbox: CRM re-post failed for call {}: {}", p.callId(), e.getMessage());
            }
        }
    }
}
