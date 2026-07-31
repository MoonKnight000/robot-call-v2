package uz.murodjon.uysotvoice.callrecord.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.agent.dialog.CallSummary;
import uz.murodjon.uysotvoice.agent.metrics.VoiceMetrics;
import uz.murodjon.uysotvoice.agent.summary.SummaryService;
import uz.murodjon.uysotvoice.crm.service.CrmClient;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;
import uz.murodjon.uysotvoice.storage.service.AudioStorageService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * Runs the post-call pipeline once a call ends (PROJECT.md §4.3, Stage 9): upload the
 * recording, summarize the transcript, write {@code call_result}, close the
 * {@code call_attempt}, and post a CRM note. Invoked from teardown on the call
 * executor, so its blocking LLM/HTTP work never touches the RTP/STT threads. Every
 * step degrades gracefully — a missing summary/storage/CRM just leaves that part out.
 */
@Component
public class CallFinalizer {

    private static final Logger log = LoggerFactory.getLogger(CallFinalizer.class);

    private final CallRecordService records;
    private final SummaryService summaryService;
    private final AudioStorageService storage;
    private final CrmClient crmClient;
    private final VoiceMetrics metrics;

    public CallFinalizer(CallRecordService records, SummaryService summaryService,
                         AudioStorageService storage, CrmClient crmClient, VoiceMetrics metrics) {
        this.records = records;
        this.summaryService = summaryService;
        this.storage = storage;
        this.crmClient = crmClient;
        this.metrics = metrics;
    }

    public void finalizeCall(long callAttemptId, long clientId, Path wav, Instant startedAt, Disposition disposition) {
        if (callAttemptId == 0) {
            return;
        }
        try {
            int durationSec = (int) Duration.between(startedAt, Instant.now()).getSeconds();
            boolean escalated = disposition == Disposition.TRANSFERRED;
            metrics.recordCallDuration(durationSec);
            metrics.disposition(disposition);

            String uploadedUrl = storage.upload(wav, wav.getFileName().toString());
            // Record where the audio actually is. With storage off, upload returns null and
            // the column used to stay empty — which meant the recording existed on disk but
            // nothing could find it, and §11.3 evidence needed shell access to retrieve.
            // A "file:" URL keeps the two cases distinguishable for the reporting API.
            String recordingUrl = uploadedUrl != null
                    ? uploadedUrl
                    : (Files.isReadable(wav) ? "file:" + wav.toAbsolutePath() : null);
            records.finishAttempt(callAttemptId, disposition, recordingUrl, durationSec);
            // Only once the upload is confirmed: deleting on a failed upload would
            // destroy the only copy of a recording that may be needed as evidence (§11.3).
            if (uploadedUrl != null) {
                storage.deleteLocalCopy(wav);
            }

            String transcript = records.transcriptText(callAttemptId);
            CallSummary summary = summaryService.summarize(transcript);
            if (summary != null) {
                Long crmNoteId = crmClient.postNote(clientId, summary);
                records.writeResult(callAttemptId, summary, escalated, crmNoteId);
                log.info("Finalized call {} (dur={}s, disposition={}, sentiment={})",
                        callAttemptId, durationSec, disposition, summary.sentiment());
            } else {
                log.info("Finalized call {} without summary (LLM unavailable or empty transcript)", callAttemptId);
            }
        } catch (Exception e) {
            log.warn("Finalization failed for call {}: {}", callAttemptId, e.getMessage());
        }
    }
}
