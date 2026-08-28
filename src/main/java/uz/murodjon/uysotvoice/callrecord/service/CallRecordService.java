package uz.murodjon.uysotvoice.callrecord.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.agent.dialog.CallSummary;
import uz.murodjon.uysotvoice.agent.dialog.DialogTechnicalSnapshot;
import uz.murodjon.uysotvoice.callrecord.repository.CallAttemptRepository;
import uz.murodjon.uysotvoice.callrecord.repository.CallResultRepository;
import uz.murodjon.uysotvoice.callrecord.repository.CallTechnicalRepository;
import uz.murodjon.uysotvoice.callrecord.repository.CallTranscriptRepository;
import uz.murodjon.uysotvoice.company.service.CurrentCompany;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Persists the call lifecycle to the database (PROJECT.md §9).
 *
 * <p>Writes happen on virtual threads (ARI/STT callbacks), and fail-open:
 * a database error is logged but must not crash the in-progress phone call.
 */
@Service
public class CallRecordService {

    private static final Logger log = LoggerFactory.getLogger(CallRecordService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final CallAttemptRepository callAttempts;
    private final CallTranscriptRepository transcripts;
    private final CallResultRepository results;
    private final CallTechnicalRepository technicalDetails;
    private final CurrentCompany company;

    /** Sequence counter per active call, evicted at attempt finish. */
    private final Map<Long, AtomicInteger> seqCounters = new ConcurrentHashMap<>();

    /**
     * Placeholder manual target id resolved once on demand and kept for the
     * lifetime of the process. Saves a round-trip per manual origination.
     */
    private final AtomicLong manualTargetId = new AtomicLong();

    /** Cached inbound target id (see {@link #inboundTargetId()}). */
    private final AtomicLong inboundTargetId = new AtomicLong();

    public CallRecordService(CallAttemptRepository callAttempts,
                             CallTranscriptRepository transcripts,
                             CallResultRepository results,
                             CallTechnicalRepository technicalDetails,
                             CurrentCompany company) {
        this.callAttempts = callAttempts;
        this.transcripts = transcripts;
        this.results = results;
        this.technicalDetails = technicalDetails;
        this.company = company;
    }

    /**
     * The company that owns {@code callAttemptId}. Read by {@code AriService.setupMedia}
     * so it can resolve the engine settings (cascade vs realtime, §11) for this specific
     * tenant rather than looking at any ambient request state.
     */
    public long companyIdOf(long callAttemptId) {
        if (callAttemptId == 0) {
            return company.id();
        }
        try {
            Long cid = callAttempts.findCompanyIdById(callAttemptId);
            return cid != null ? cid : company.id();
        } catch (Exception e) {
            log.warn("companyIdOf failed for call {}: {}", callAttemptId, e.getMessage());
            return company.id();
        }
    }

    public long targetIdOf(long callAttemptId) {
        if (callAttemptId == 0) {
            return 0L;
        }
        try {
            Long tid = callAttempts.findTargetIdById(callAttemptId);
            return tid != null ? tid : 0L;
        } catch (Exception e) {
            log.warn("targetIdOf failed for call {}: {}", callAttemptId, e.getMessage());
            return 0L;
        }
    }

    /**
     * Id of the placeholder target seeded by {@code V5} (phone = 'MANUAL'); cached.
     * Manual calls placed through the REST API use this as their {@code
     * call_attempt.target_id} so the row satisfies the NOT NULL FK while making
     * it obvious the call was not part of an automated campaign (§8.4).
     */
    public long manualTargetId() {
        long cached = manualTargetId.get();
        if (cached != 0) {
            return cached;
        }
        try {
            return callAttempts.findTargetIdByPhone("MANUAL")
                    .map(id -> {
                        manualTargetId.set(id);
                        return id;
                    })
                    .orElse(0L);
        } catch (Exception e) {
            log.warn("Manual target lookup failed: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Id of the placeholder target seeded by {@code V5} (phone = 'INBOUND'); cached.
     * Gives inbound calls their own {@code call_attempt} parent, distinct from manual
     * REST test calls (which use {@link #manualTargetId()}), so reports can tell them
     * apart (ROADMAP C.1).
     */
    public long inboundTargetId() {
        long cached = inboundTargetId.get();
        if (cached != 0) {
            return cached;
        }
        try {
            return callAttempts.findTargetIdByPhone("INBOUND")
                    .map(id -> {
                        inboundTargetId.set(id);
                        return id;
                    })
                    .orElse(0L);
        } catch (Exception e) {
            log.warn("Inbound target lookup failed: {}", e.getMessage());
            return 0;
        }
    }

    /** As {@link #startAttempt(long, String, String, Long)}, for calls with no inbound route (outbound/manual). */
    public long startAttempt(long targetId, String channelId, String language) {
        return startAttempt(targetId, channelId, language, null);
    }

    /**
     * Insert a call_attempt (answered now); returns its id, or 0 on failure.
     *
     * @param inboundRouteId the route this call matched (ROADMAP C.1, §10.9 stats
     *                       drawer), or null for an outbound/manual call
     */
    public long startAttempt(long targetId, String channelId, String language, Long inboundRouteId) {
        if (targetId == 0) {
            return 0;
        }
        try {
            return callAttempts.startAttempt(targetId, channelId, language, inboundRouteId, company.id());
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
            transcripts.save(callId, seq, role, text, dialogState, tsOffsetMs, confidence);
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
            StringBuilder sb = new StringBuilder();
            for (String line : transcripts.transcriptLines(callId)) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("transcriptText failed for call {}: {}", callId, e.getMessage());
            return "";
        }
    }

    /**
     * §15 "Bugun" mini-statistika — record which panel user answered a transferred call
     * ({@code AriService.handleOperatorJoin}). Best-effort like every other write here.
     */
    public void assignOperator(long callId, long userId) {
        if (callId == 0) {
            return;
        }
        try {
            callAttempts.assignOperator(callId, userId);
        } catch (Exception e) {
            log.warn("assignOperator failed for call {}: {}", callId, e.getMessage());
        }
    }

    /** Close out a call_attempt with its outcome. */
    public void finishAttempt(long callId, Disposition disposition, Long recordingFileId, int durationSec) {
        seqCounters.remove(callId);
        if (callId == 0) {
            return;
        }
        try {
            callAttempts.finishAttempt(callId, Instant.now(), durationSec, disposition, recordingFileId);
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
            // Dual-write (ROADMAP A.3): the fixed reason_code/promised_date/promised_amount
            // columns stay populated whenever the scenario's outcome happens to use those
            // exact names (debt-collection does), so CrmClient/CSV export keep working
            // unmodified; every scenario's outcome — including these — also lands in the
            // generic outcome JSONB column below.
            results.insertIgnoringConflict(
                    callId,
                    s.summary() != null ? s.summary() : "",
                    asString(s.outcome().get("reasonCode")),
                    asDate(s.outcome().get("promisedDate")),
                    asAmount(s.outcome().get("promisedAmount")),
                    s.sentiment() != null ? s.sentiment().name() : null,
                    s.needsFollowUp(),
                    s.followUpNote(),
                    escalated,
                    crmNoteId,
                    toJson(s.outcome()));
            log.info("call_result written for call {} (disposition-escalated={})", callId, escalated);
        } catch (Exception e) {
            log.warn("writeResult failed for call {}: {}", callId, e.getMessage());
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static LocalDate asDate(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value.toString());
        } catch (Exception e) {
            log.warn("outcome.promisedDate '{}' is not a valid date: {}", value, e.getMessage());
            return null;
        }
    }

    private static BigDecimal asAmount(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            log.warn("outcome.promisedAmount '{}' is not a valid number: {}", value, e.getMessage());
            return null;
        }
    }

    private static String toJson(Map<String, Object> outcome) {
        try {
            return JSON.writeValueAsString(outcome);
        } catch (Exception e) {
            log.warn("Could not serialize call outcome: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Insert the single call_technical row (§10.5 "Texnik" tab). Written once, from
     * {@code CallFinalizer} at teardown — unlike {@link #writeResult}, nothing re-runs
     * this later, so a plain insert is enough.
     */
    public void writeTechnicalDetail(long callId, String channelName, String trunk, String amdResult,
                                     String sttProvider, String ttsProvider, String ttsVoice,
                                     String llmModel, DialogTechnicalSnapshot technical) {
        if (callId == 0) {
            return;
        }
        try {
            technicalDetails.save(callId, channelName, trunk, amdResult, sttProvider, ttsProvider, ttsVoice,
                    llmModel, technical);
        } catch (Exception e) {
            log.warn("writeTechnicalDetail failed for call {}: {}", callId, e.getMessage());
        }
    }
}
