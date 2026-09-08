package uz.murodjon.robotcallv2.callrecord.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.agent.dialog.CallSummary;
import uz.murodjon.robotcallv2.agent.summary.SummaryService;
import uz.murodjon.robotcallv2.callrecord.application.dto.PendingNote;
import uz.murodjon.robotcallv2.callrecord.application.dto.PendingSummary;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallOutboxRepository;
import uz.murodjon.robotcallv2.crm.application.service.CrmClient;
import uz.murodjon.robotcallv2.scenario.application.service.ScenarioService;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;

import java.util.List;

/**
 * Retries the post-call work that failed the first time (PROJECT.md §4.3, Stage 9).
 */
@Service
public class CallOutboxService {

    private static final Logger log = LoggerFactory.getLogger(CallOutboxService.class);

    private final CallOutboxRepository outbox;
    private final CallRecordService records;
    private final SummaryService summaryService;
    private final CrmClient crmClient;
    private final ScenarioService scenarioService;
    private final CallMemoryWriter memoryWriter;
    private final boolean enabled;
    private final int maxAttempts;
    private final int batch;

    public CallOutboxService(CallOutboxRepository outbox,
                             CallRecordService records,
                             SummaryService summaryService,
                             CrmClient crmClient,
                             ScenarioService scenarioService,
                             CallMemoryWriter memoryWriter,
                             @Value("${voice-agent.outbox.enabled:true}") boolean enabled,
                             @Value("${voice-agent.outbox.max-attempts:5}") int maxAttempts,
                             @Value("${voice-agent.outbox.batch:20}") int batch) {
        this.outbox = outbox;
        this.records = records;
        this.summaryService = summaryService;
        this.crmClient = crmClient;
        this.scenarioService = scenarioService;
        this.memoryWriter = memoryWriter;
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
        List<PendingSummary> pending =
                outbox.attemptsAwaitingSummary(maxAttempts, batch);
        if (pending.isEmpty()) {
            return;
        }
        log.info("Outbox: {} call(s) awaiting a summary", pending.size());
        for (PendingSummary p : pending) {
            outbox.countSummaryAttempt(p.callId());
            try {
                String transcript = records.transcriptText(p.callId());
                long companyId = records.companyIdOf(p.callId());
                Scenario scenario = scenarioService.requireScenario(companyId, p.scenarioId());
                CallSummary summary = summaryService.summarize(transcript, scenario.definition());
                if (summary == null) {
                    log.debug("Outbox: summary still unavailable for call {}", p.callId());
                    continue;
                }
                String noteId = crmClient.postNote(companyId, p.clientId(), summary);
                records.writeResult(p.callId(), summary, false, noteId);
                memoryWriter.remember(p.callId(), companyId, scenario, p.disposition(), summary);
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
        List<PendingNote> pending = outbox.notesAwaitingCrm(maxAttempts, batch);
        if (pending.isEmpty()) {
            return;
        }
        log.info("Outbox: {} CRM note(s) to re-post", pending.size());
        for (PendingNote p : pending) {
            try {
                String noteId = crmClient.postNote(records.companyIdOf(p.callId()), p.clientId(), p.summary());
                if (noteId != null) {
                    outbox.markCrmPosted(p.callId(), noteId);
                    log.info("Outbox: CRM note posted for call {} (noteId={})", p.callId(), noteId);
                } else {
                    outbox.markCrmFailed(p.callId(), "CRM did not enqueue the note");
                }
            } catch (Exception e) {
                outbox.markCrmFailed(p.callId(), e.getMessage());
                log.warn("Outbox: CRM re-post failed for call {}: {}", p.callId(), e.getMessage());
            }
        }
    }
}

