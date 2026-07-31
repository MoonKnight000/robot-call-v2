package uz.murodjon.uysotvoice.callrecord.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.agent.dialog.CallSummary;
import uz.murodjon.uysotvoice.callrecord.entity.CallAttempt;
import uz.murodjon.uysotvoice.callrecord.entity.CallTranscript;
import uz.murodjon.uysotvoice.callrecord.repository.CallAttemptJpaRepository;
import uz.murodjon.uysotvoice.callrecord.repository.CallResultJpaRepository;
import uz.murodjon.uysotvoice.callrecord.repository.CallTranscriptJpaRepository;
import uz.murodjon.uysotvoice.campaign.repository.CampaignTargetJpaRepository;
import uz.murodjon.uysotvoice.company.service.CurrentCompany;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Persists the call lifecycle to Postgres (PROJECT.md §6, Stage 9): a
 * {@code call_attempt} per call, {@code call_transcript} per utterance, and one
 * {@code call_result}. JPA-backed against the known Flyway schema ({@code ddl-auto: validate}).
 *
 * <p>All writes are best-effort: a DB error is logged and swallowed so it never
 * breaks the live call. {@link #startAttempt} returns {@code 0} on failure, which
 * downstream treats as "no record".
 */
@Service
public class CallRecordService {

    private static final Logger log = LoggerFactory.getLogger(CallRecordService.class);

    private final CallAttemptJpaRepository callAttempts;
    private final CallTranscriptJpaRepository transcripts;
    private final CallResultJpaRepository results;
    private final CampaignTargetJpaRepository targets;
    private final CurrentCompany company;
    private final AtomicLong manualTargetId = new AtomicLong(0);
    private final Map<Long, AtomicInteger> seqCounters = new ConcurrentHashMap<>();

    public CallRecordService(CallAttemptJpaRepository callAttempts, CallTranscriptJpaRepository transcripts,
                             CallResultJpaRepository results, CampaignTargetJpaRepository targets,
                             CurrentCompany company) {
        this.callAttempts = callAttempts;
        this.transcripts = transcripts;
        this.results = results;
        this.targets = targets;
        this.company = company;
    }

    /** Id of the placeholder target seeded by {@code V2} (phone = 'MANUAL'); cached. */
    public long manualTargetId() {
        long cached = manualTargetId.get();
        if (cached != 0) {
            return cached;
        }
        try {
            return targets.findFirstByPhoneOrderById("MANUAL")
                    .map(t -> {
                        manualTargetId.set(t.getId());
                        return t.getId();
                    })
                    .orElse(0L);
        } catch (Exception e) {
            log.warn("Manual target lookup failed: {}", e.getMessage());
            return 0;
        }
    }

    /** Insert a call_attempt (answered now); returns its id, or 0 on failure. */
    public long startAttempt(long targetId, String channelId, String language) {
        if (targetId == 0) {
            return 0;
        }
        try {
            Instant now = Instant.now();
            CallAttempt entity = new CallAttempt();
            entity.setTargetId(targetId);
            entity.setAsteriskChannel(channelId);
            entity.setLanguage(language);
            entity.setStartedAt(now);
            entity.setAnsweredAt(now);
            entity.setCreatedAt(now);
            entity.setCompanyId(company.id());
            return callAttempts.save(entity).getId();
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
            CallTranscript entity = new CallTranscript();
            entity.setCallId(callId);
            entity.setSeq(seq);
            entity.setRole(role);
            entity.setText(text);
            entity.setDialogState(dialogState);
            entity.setTsOffsetMs(Math.max(0, tsOffsetMs));
            entity.setSttConfidence(confidence);
            transcripts.save(entity);
        } catch (Exception e) {
            log.warn("addTranscript failed for call {}: {}", callId, e.getMessage());
        }
    }

    /**
     * Record why a call failed technically, on the attempt row. Without this the
     * {@code error_message} column stays empty and a call that died during media
     * setup is indistinguishable from one the client simply did not answer.
     */
    public void recordError(long callId, String message) {
        if (callId == 0 || message == null || message.isBlank()) {
            return;
        }
        try {
            callAttempts.recordError(callId, message);
        } catch (Exception e) {
            log.warn("recordError failed for call {}: {}", callId, e.getMessage());
        }
    }

    /**
     * Store the Asterisk hangup cause for the attempt on {@code channelId} (§8.6).
     *
     * <p>Keyed by channel rather than by attempt id because the cause arrives on
     * {@code ChannelDestroyed}, which fires after the attempt has already been closed out
     * — and for a call that was never answered there is no attempt row in memory at all.
     */
    public void recordHangupCause(String channelId, String cause) {
        if (channelId == null || cause == null) {
            return;
        }
        try {
            callAttempts.recordHangupCause(channelId, cause);
        } catch (Exception e) {
            log.warn("recordHangupCause failed for channel {}: {}", channelId, e.getMessage());
        }
    }

    /** Full transcript as {@code ROLE: text} lines, in order (for the summary LLM). */
    public String transcriptText(long callId) {
        if (callId == 0) {
            return "";
        }
        try {
            List<CallTranscript> rows = transcripts.findByCallIdOrderBySeq(callId);
            StringBuilder sb = new StringBuilder();
            for (CallTranscript row : rows) {
                sb.append(row.getRole()).append(": ").append(row.getText()).append('\n');
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
            callAttempts.finishAttempt(callId, Instant.now(), durationSec,
                    disposition != null ? disposition.name() : null, recordingUrl);
        } catch (Exception e) {
            log.warn("finishAttempt failed for call {}: {}", callId, e.getMessage());
        }
    }

    /**
     * Insert the single call_result row (§4.3).
     *
     * <p>Idempotent: the outbox re-runs the summary for calls that have no result yet, and
     * two instances sweeping at once would otherwise have one of them fail on the unique
     * {@code call_id}. Whichever writes first wins — they are summarizing the same
     * transcript.
     */
    public void writeResult(long callId, CallSummary s, boolean escalated, Long crmNoteId) {
        if (callId == 0 || s == null) {
            return;
        }
        try {
            results.insertIgnoringConflict(
                    callId,
                    s.summary() != null ? s.summary() : "",
                    s.reasonCode() != null ? s.reasonCode().name() : null,
                    s.promisedDate(),
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
