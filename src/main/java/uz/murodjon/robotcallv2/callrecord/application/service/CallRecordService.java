package uz.murodjon.robotcallv2.callrecord.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.dialog.CallSummary;
import uz.murodjon.robotcallv2.agent.dialog.DialogTechnicalSnapshot;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallAttemptRepository;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallResultRepository;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallTechnicalRepository;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallTranscriptRepository;
import uz.murodjon.robotcallv2.callrecord.domain.entity.*;
import uz.murodjon.robotcallv2.company.infrastructure.config.CompanyProperties;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.dialog.ReasonCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
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
    private final CompanyProperties companyProperties;

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
                             CompanyProperties companyProperties) {
        this.callAttempts = callAttempts;
        this.transcripts = transcripts;
        this.results = results;
        this.technicalDetails = technicalDetails;
        this.companyProperties = companyProperties;
    }

    /**
     * The company that owns {@code callAttemptId}. Read by {@code AriService.setupMedia}
     * so it can resolve the engine settings (cascade vs realtime, §11) for this specific
     * tenant. Falls back to the default company only when there is no attempt row to ask.
     */
    public long companyIdOf(long callAttemptId) {
        if (callAttemptId == 0) {
            return companyProperties.defaultId();
        }
        try {
            Long cid = callAttempts.findCompanyIdById(callAttemptId);
            return cid != null ? cid : companyProperties.defaultId();
        } catch (Exception e) {
            log.warn("companyIdOf failed for call {}: {}", callAttemptId, e.getMessage());
            return companyProperties.defaultId();
        }
    }

    /**
     * Answered calls this company made to that number between two instants, newest first.
     *
     * <p>Exposed for conversion attribution: an event reported days after a call has to
     * find the call that could have caused it, and that search is by number and time.
     */
    public List<AnsweredCall> findAnsweredCalls(long companyId, String phone, Instant from, Instant to) {
        if (phone == null || phone.isBlank() || from == null || to == null) {
            return List.of();
        }
        try {
            return callAttempts.findAnsweredByPhone(companyId, phone, from, to);
        } catch (Exception e) {
            log.warn("findAnsweredCalls failed for {}: {}", phone, e.getMessage());
            return List.of();
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
     * Whether the call came in rather than went out. Unknown attempts read as outbound:
     * this platform places far more calls than it answers, and the answer only labels a
     * CRM call-history row.
     */
    public boolean isInbound(long callAttemptId) {
        if (callAttemptId == 0) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(callAttempts.findInboundById(callAttemptId));
        } catch (Exception e) {
            log.warn("isInbound failed for call {}: {}", callAttemptId, e.getMessage());
            return false;
        }
    }

    /** The number the attempt talked to; {@code null} when there is no such attempt. */
    public String phoneOf(long callAttemptId) {
        if (callAttemptId == 0) {
            return null;
        }
        try {
            return callAttempts.findPhoneById(callAttemptId);
        } catch (Exception e) {
            log.warn("phoneOf failed for call {}: {}", callAttemptId, e.getMessage());
            return null;
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

    /**
     * Open a call_attempt; returns its id, or 0 on failure.
     *
     * <p>Written when the call is dialled, not when it is answered, so that a call
     * nobody ever picked up — and one the carrier refused outright — still leaves a row
     * for the calls page. {@code answered_at} stays null until {@link #markAnswered}.
     *
     * @param companyId    whose call this is; passed in rather than read from the current
     *                     request, because the dialer originates on a queue thread for
     *                     whichever company owns the campaign
     * @param phone        the dialled number, the only record of it when {@code targetId}
     *                     is one of the MANUAL/INBOUND placeholders
     * @param inboundRouteId the route this call matched (ROADMAP C.1, §10.9 stats
     *                       drawer), or null for an outbound/manual call
     * @param variantId      the A/B variant this call runs, or null when its campaign is
     *                       not testing. Written now rather than derived later: a
     *                       conversion reported days afterwards has to be creditable to
     *                       the script that earned it
     */
    public long startAttempt(long companyId, long targetId, String channelId, String phone,
                             String language, Long inboundRouteId, Long variantId) {
        if (targetId == 0) {
            return 0;
        }
        try {
            return callAttempts.create(
                    CallAttempt.starting(companyId, targetId, channelId, phone, language, inboundRouteId, variantId));
        } catch (Exception e) {
            log.warn("startAttempt failed for {}: {}", channelId, e.getMessage());
            return 0;
        }
    }

    /**
     * Record an attempt that never became a call — the number was blocked, the scenario
     * was gone, or the originate itself failed. The dialer has already spent one of the
     * target's attempts by the time any of that is known, so the row has to exist or the
     * call list silently loses an attempt and {@code reason} is nowhere to be read.
     *
     * @param reason what stopped the call, written to {@code error_message}
     */
    public long recordUnplacedAttempt(long companyId, long targetId, String phone, String language,
                                      Disposition disposition, String reason) {
        if (targetId == 0) {
            return 0;
        }
        try {
            return callAttempts.create(CallAttempt.unplaced(companyId, targetId, phone, language, disposition, reason));
        } catch (Exception e) {
            log.warn("recordUnplacedAttempt failed for target {} ({}): {}", targetId, phone, e.getMessage());
            return 0;
        }
    }

    /** The attempt opened for {@code channelId} when the call was dialled, or 0. */
    public long findAttemptIdByChannel(String channelId) {
        try {
            return callAttempts.findIdByChannel(channelId).orElse(0L);
        } catch (Exception e) {
            log.warn("findAttemptIdByChannel failed for {}: {}", channelId, e.getMessage());
            return 0;
        }
    }

    /**
     * Close out the attempt for a call nobody picked up. A no-op once the call has been
     * answered, so it is safe to call for every channel that goes away.
     */
    public void finishUnansweredAttempt(String channelId, Disposition disposition) {
        try {
            callAttempts.finishUnanswered(channelId, Instant.now(), disposition);
        } catch (Exception e) {
            log.warn("finishUnanswered failed for {}: {}", channelId, e.getMessage());
        }
    }

    /** Stamp the moment the call was picked up. */
    public void markAnswered(long callId) {
        if (callId == 0) {
            return;
        }
        try {
            callAttempts.markAnswered(callId, Instant.now());
        } catch (Exception e) {
            log.warn("markAnswered failed for call {}: {}", callId, e.getMessage());
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
            transcripts.save(CallTranscript.line(callId, seq, role, text, dialogState, tsOffsetMs, confidence));
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
     * Record that this call ended up being spoken in {@code language} — the caller
     * answered in the other language of a bilingual campaign, so the language it was
     * dialled in is no longer the one it was held in. Reports and the recording's
     * transcript are read against this column.
     */
    public void updateLanguage(long callId, String language) {
        if (callId == 0 || language == null || language.isBlank()) {
            return;
        }
        try {
            callAttempts.updateLanguage(callId, language);
        } catch (Exception e) {
            log.warn("updateLanguage failed for call {}: {}", callId, e.getMessage());
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
            for (String line : transcripts.findTranscriptLines(callId)) {
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
    public void writeResult(long callId, CallSummary s, boolean escalated, String crmNoteId) {
        if (callId == 0 || s == null) {
            return;
        }
        try {
            results.insertIgnoringConflict(CallResult.summaryOf(
                    callId,
                    s.summary() != null ? s.summary() : "",
                    asReasonCode(s.outcome().get("reasonCode")),
                    asDate(s.outcome().get("promisedDate")),
                    asAmount(s.outcome().get("promisedAmount")),
                    s.sentiment(),
                    s.needsFollowUp(),
                    s.followUpNote(),
                    escalated,
                    crmNoteId,
                    toJson(s.outcome())));
            log.info("call_result written for call {} (disposition-escalated={})", callId, escalated);
        } catch (Exception e) {
            log.warn("writeResult failed for call {}: {}", callId, e.getMessage());
        }
    }

    /**
     * The model answers with free text, so an unrecognised code is dropped rather than
     * stored: the column is an enum, and a value outside it cannot be read back at all.
     */
    private static ReasonCode asReasonCode(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return ReasonCode.valueOf(value.toString().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("outcome.reasonCode '{}' is not a known ReasonCode", value);
            return null;
        }
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
            CallTechnical row = CallTechnical.of(callId, channelName, trunk, amdResult, sttProvider, ttsProvider,
                    ttsVoice, llmModel);
            if (technical != null) {
                row = row.withCounters((int) technical.promptTokens(), (int) technical.completionTokens(),
                        (int) technical.cachedTokens(), technical.turnCount(),
                        technical.avgTurnLatencyMs(), technical.maxTurnLatencyMs(),
                        technical.avgLlmLatencyMs(), technical.maxLlmLatencyMs());
            }
            technicalDetails.save(row);
        } catch (Exception e) {
            log.warn("writeTechnicalDetail failed for call {}: {}", callId, e.getMessage());
        }
    }
}
