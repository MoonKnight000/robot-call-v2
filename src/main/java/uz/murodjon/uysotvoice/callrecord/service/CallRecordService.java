package uz.murodjon.uysotvoice.callrecord.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.agent.dialog.CallSummary;
import uz.murodjon.uysotvoice.agent.dialog.DialogTechnicalSnapshot;
import uz.murodjon.uysotvoice.callrecord.entity.CallAttemptEntity;
import uz.murodjon.uysotvoice.callrecord.entity.CallTechnicalEntity;
import uz.murodjon.uysotvoice.callrecord.entity.CallTranscriptEntity;
import uz.murodjon.uysotvoice.callrecord.repository.CallAttemptJpaRepository;
import uz.murodjon.uysotvoice.callrecord.repository.CallResultJpaRepository;
import uz.murodjon.uysotvoice.callrecord.repository.CallTechnicalJpaRepository;
import uz.murodjon.uysotvoice.callrecord.repository.CallTranscriptJpaRepository;
import uz.murodjon.uysotvoice.campaign.repository.CampaignTargetJpaRepository;
import uz.murodjon.uysotvoice.company.service.CurrentCompany;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
    private static final ObjectMapper JSON = new ObjectMapper();

    private final CallAttemptJpaRepository callAttempts;
    private final CallTranscriptJpaRepository transcripts;
    private final CallResultJpaRepository results;
    private final CallTechnicalJpaRepository technicalDetails;
    private final CampaignTargetJpaRepository targets;
    private final CurrentCompany company;
    private final AtomicLong manualTargetId = new AtomicLong(0);
    private final AtomicLong inboundTargetId = new AtomicLong(0);
    private final Map<Long, AtomicInteger> seqCounters = new ConcurrentHashMap<>();

    public CallRecordService(CallAttemptJpaRepository callAttempts, CallTranscriptJpaRepository transcripts,
                             CallResultJpaRepository results, CallTechnicalJpaRepository technicalDetails,
                             CampaignTargetJpaRepository targets, CurrentCompany company) {
        this.callAttempts = callAttempts;
        this.transcripts = transcripts;
        this.results = results;
        this.technicalDetails = technicalDetails;
        this.targets = targets;
        this.company = company;
    }

    /**
     * The company {@code callAttemptId} was recorded under (§11 ai-model settings) —
     * {@code DialogEngine} resolves this once per call to look up its overrides. Falls
     * back to {@link CurrentCompany} for {@code 0} (no persisted attempt, e.g. a manual
     * test call whose insert failed) or an attempt somehow missing from the table.
     */
    public long companyIdOf(long callAttemptId) {
        if (callAttemptId == 0) {
            return company.id();
        }
        Long companyId = callAttempts.findCompanyIdById(callAttemptId);
        return companyId != null ? companyId : company.id();
    }

    /** Id of the placeholder target seeded by {@code V1} (phone = 'MANUAL'); cached. */
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
            return targets.findFirstByPhoneOrderById("INBOUND")
                    .map(t -> {
                        inboundTargetId.set(t.getId());
                        return t.getId();
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
            Instant now = Instant.now();
            CallAttemptEntity entity = new CallAttemptEntity();
            entity.setTarget(targets.getReferenceById(targetId));
            entity.setAsteriskChannel(channelId);
            entity.setLanguage(language);
            entity.setStartedAt(now);
            entity.setAnsweredAt(now);
            entity.setCreatedAt(now);
            entity.setCompanyId(company.id());
            entity.setInboundRouteId(inboundRouteId);
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
            CallTranscriptEntity entity = new CallTranscriptEntity();
            entity.setCall(callAttempts.getReferenceById(callId));
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
            List<CallTranscriptEntity> rows = transcripts.findByCall_IdOrderBySeq(callId);
            StringBuilder sb = new StringBuilder();
            for (CallTranscriptEntity row : rows) {
                sb.append(row.getRole()).append(": ").append(row.getText()).append('\n');
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
            CallTechnicalEntity entity = new CallTechnicalEntity();
            entity.setCall(callAttempts.getReferenceById(callId));
            entity.setChannelName(channelName);
            entity.setTrunk(trunk);
            entity.setAmdResult(amdResult);
            entity.setSttProvider(sttProvider);
            entity.setTtsProvider(ttsProvider);
            entity.setTtsVoice(ttsVoice);
            entity.setLlmModel(llmModel);
            if (technical != null) {
                entity.setPromptTokens((int) technical.promptTokens());
                entity.setCompletionTokens((int) technical.completionTokens());
                entity.setCachedTokens((int) technical.cachedTokens());
                entity.setTurnCount(technical.turnCount());
                entity.setAvgTurnLatencyMs(technical.avgTurnLatencyMs());
                entity.setMaxTurnLatencyMs(technical.maxTurnLatencyMs());
                entity.setAvgLlmLatencyMs(technical.avgLlmLatencyMs());
                entity.setMaxLlmLatencyMs(technical.maxLlmLatencyMs());
            }
            entity.setCreatedAt(Instant.now());
            technicalDetails.save(entity);
        } catch (Exception e) {
            log.warn("writeTechnicalDetail failed for call {}: {}", callId, e.getMessage());
        }
    }
}
