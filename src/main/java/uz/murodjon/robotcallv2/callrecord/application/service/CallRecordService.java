package uz.murodjon.robotcallv2.callrecord.application.service;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.agent.dialog.CallSummary;
import uz.murodjon.robotcallv2.agent.dialog.DialogTechnicalSnapshot;
import uz.murodjon.robotcallv2.agent.rtp.RtpQuality;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallTranscriptUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallAttemptRepository;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallResultRepository;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallTechnicalRepository;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallTranscriptRepository;
import uz.murodjon.robotcallv2.callrecord.application.port.output.OutboxMessageRepository;
import uz.murodjon.robotcallv2.callrecord.domain.entity.*;
import uz.murodjon.robotcallv2.company.infrastructure.config.CompanyProperties;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.dialog.ReasonCode;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Persists the call lifecycle to the database (PROJECT.md §9).
 *
 * <p>Writes happen on virtual threads (ARI/STT callbacks), and fail-open:
 * a database error is logged but must not crash the in-progress phone call.
 */
@Service
public class CallRecordService implements CallAttemptUseCase, CallTranscriptUseCase {

    private static final Logger log = LoggerFactory.getLogger(CallRecordService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final CallAttemptRepository callAttemptRepository;
    private final CallTranscriptRepository callTranscriptRepository;
    private final CallResultRepository callResultRepository;
    private final CallTechnicalRepository callTechnicalRepository;
    private final CompanyProperties companyProperties;
    /** Where the call's CRM note is queued, in the transaction that stores its result. */
    private final OutboxMessageRepository outboxMessageRepository;

    /** Sequence counter per active call, evicted at attempt finish. */
    private final Map<Long, AtomicInteger> seqCounters = new ConcurrentHashMap<>();

    public CallRecordService(CallAttemptRepository callAttemptRepository,
                             CallTranscriptRepository callTranscriptRepository,
                             CallResultRepository callResultRepository,
                             CallTechnicalRepository callTechnicalRepository,
                             CompanyProperties companyProperties,
                             OutboxMessageRepository outboxMessageRepository) {
        this.callAttemptRepository = callAttemptRepository;
        this.callTranscriptRepository = callTranscriptRepository;
        this.callResultRepository = callResultRepository;
        this.callTechnicalRepository = callTechnicalRepository;
        this.companyProperties = companyProperties;
        this.outboxMessageRepository = outboxMessageRepository;
    }

    /**
     * The company that owns {@code callAttemptId}. Read by {@code CallSetupService.setupMedia}
     * so it can resolve the engine settings (cascade vs realtime, §11) for this specific
     * tenant. Falls back to the default company only when there is no attempt row to ask.
     */
    @Override
    public long findCompanyIdByAttemptId(long callAttemptId) {
        if (callAttemptId == 0) {
            return companyProperties.defaultId();
        }
        // A database error propagates rather than answering "the default company": every
        // caller uses the answer to pick whose config, CRM and storage the call goes to, and
        // guessing filed one tenant's call — and posted its CRM note — under another.
        Long cid = callAttemptRepository.findCompanyIdById(callAttemptId);
        return cid != null ? cid : companyProperties.defaultId();
    }

    /**
     * Answered calls this company made to that number between two instants, newest first.
     *
     * <p>Exposed for conversion attribution: an event reported days after a call has to
     * find the call that could have caused it, and that search is by number and time.
     */
    @Override
    public List<AnsweredCall> findAnsweredCalls(long companyId, String phone, Instant from, Instant to) {
        if (phone == null || phone.isBlank() || from == null || to == null) {
            return List.of();
        }
        try {
            return callAttemptRepository.findAnsweredByPhone(companyId, phone, from, to);
        } catch (Exception e) {
            log.warn("findAnsweredCalls failed for {}: {}", PhoneMasker.mask(phone), e.getMessage());
            return List.of();
        }
    }

    /**
     * What the company's contact policy needs to know about one number; empty when the
     * history cannot be read — a failed lookup lets the call through, as the dialer's other
     * best-effort checks do.
     */
    @Override
    public ContactHistory findContactHistory(long companyId, String phone, Instant since, int attemptLimit) {
        if (phone == null || phone.isBlank()) {
            return ContactHistory.NONE;
        }
        try {
            return callAttemptRepository.findContactHistory(companyId, phone, since, Math.max(1, attemptLimit));
        } catch (Exception e) {
            log.warn("findContactHistory failed for {}: {}", PhoneMasker.mask(phone), e.getMessage());
            return ContactHistory.NONE;
        }
    }

    public long findTargetIdByAttemptId(long callAttemptId) {
        if (callAttemptId == 0) {
            return 0L;
        }
        try {
            Long tid = callAttemptRepository.findTargetIdById(callAttemptId);
            return tid != null ? tid : 0L;
        } catch (Exception e) {
            log.warn("findTargetIdByAttemptId failed for call {}: {}", callAttemptId, e.getMessage());
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
            return Boolean.TRUE.equals(callAttemptRepository.findInboundById(callAttemptId));
        } catch (Exception e) {
            log.warn("isInbound failed for call {}: {}", callAttemptId, e.getMessage());
            return false;
        }
    }

    /** The inbound route an attempt came in on; {@code null} for an outbound call or on failure. */
    @Override
    public Long findInboundRouteIdByAttemptId(long callAttemptId) {
        if (callAttemptId == 0) {
            return null;
        }
        try {
            return callAttemptRepository.findInboundRouteIdById(callAttemptId);
        } catch (Exception e) {
            log.warn("findInboundRouteIdByAttemptId failed for call {}: {}", callAttemptId, e.getMessage());
            return null;
        }
    }

    /** The number the attempt talked to; {@code null} when there is no such attempt. */
    @Override
    public String findPhoneByAttemptId(long callAttemptId) {
        if (callAttemptId == 0) {
            return null;
        }
        try {
            return callAttemptRepository.findPhoneById(callAttemptId);
        } catch (Exception e) {
            log.warn("findPhoneByAttemptId failed for call {}: {}", callAttemptId, e.getMessage());
            return null;
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
     * @param targetId     the campaign target, or {@link CallAttempt#NO_TARGET} for a call no campaign
     *                     placed (manual, test, widget, operator, inbound)
     * @param phone        the dialled number, the only record of it when there is no target
     * @param inboundRouteId the route this call matched (ROADMAP C.1, §10.9 stats
     *                       drawer), or null for an outbound/manual call
     * @param variantId      the A/B variant this call runs, or null when its campaign is
     *                       not testing. Written now rather than derived later: a
     *                       conversion reported days afterwards has to be creditable to
     *                       the script that earned it
     */
    @Override
    public long startAttempt(long companyId, long targetId, String channelId, String phone,
                             String language, Long inboundRouteId, Long variantId) {
        try {
            return callAttemptRepository.create(
                    CallAttempt.starting(companyId, targetId, channelId, phone, language, inboundRouteId, variantId));
        } catch (Exception e) {
            log.warn("startAttempt failed for {}: {}", channelId, e.getMessage());
            return 0;
        }
    }

    /**
     * Which agent, and which published version of it, ran the call (NEXT-PLAN A.1). Best
     * effort: a failure here must not stop the conversation from starting.
     */
    @Override
    public void recordAgent(long callId, AiAgent agent) {
        if (callId == 0 || agent == null || agent.id() == null || agent.id() == 0L) {
            return;
        }
        try {
            callAttemptRepository.recordAgent(callId, agent.id(), agent.publishedVersion());
        } catch (Exception e) {
            log.warn("[{}] agent version not recorded: {}", callId, e.getMessage());
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
    @Override
    public long recordUnplacedAttempt(long companyId, long targetId, String phone, String language,
                                      Disposition disposition, String reason) {
        try {
            return callAttemptRepository.create(CallAttempt.unplaced(companyId, targetId, phone, language, disposition, reason));
        } catch (Exception e) {
            log.warn("recordUnplacedAttempt failed for target {} ({}): {}", targetId, PhoneMasker.mask(phone),
                    e.getMessage());
            return 0;
        }
    }

    /** The attempt opened for {@code channelId} when the call was dialled, or 0. */
    @Override
    public long findAttemptIdByChannel(String channelId) {
        try {
            return callAttemptRepository.findIdByChannel(channelId).orElse(0L);
        } catch (Exception e) {
            log.warn("findAttemptIdByChannel failed for {}: {}", channelId, e.getMessage());
            return 0;
        }
    }

    /**
     * Close out the attempt for a call nobody picked up. A no-op once the call has been
     * answered, so it is safe to call for every channel that goes away.
     */
    @Override
    public void finishUnansweredAttempt(String channelId, Disposition disposition) {
        try {
            callAttemptRepository.finishUnanswered(channelId, Instant.now(), disposition);
        } catch (Exception e) {
            log.warn("finishUnanswered failed for {}: {}", channelId, e.getMessage());
        }
    }

    /** Stamp the moment the call was picked up. */
    @Override
    public void markAnswered(long callId) {
        if (callId == 0) {
            return;
        }
        try {
            callAttemptRepository.markAnswered(callId, Instant.now());
        } catch (Exception e) {
            log.warn("markAnswered failed for call {}: {}", callId, e.getMessage());
        }
    }

    /** Insert one transcript line; seq is auto-assigned per call. */
    @Override
    public void addTranscript(long callId, String role, String text, String dialogState,
                              int tsOffsetMs, Float confidence) {
        if (callId == 0 || text == null || text.isBlank()) {
            return;
        }
        int seq = seqCounters.computeIfAbsent(callId, k -> new AtomicInteger()).incrementAndGet();
        try {
            callTranscriptRepository.save(CallTranscript.line(callId, seq, role, text, dialogState, tsOffsetMs, confidence));
        } catch (Exception e) {
            log.warn("addTranscript failed for call {}: {}", callId, e.getMessage());
        }
    }

    /**
     * Record why a call failed technically, on the attempt row. Without this the
     * {@code error_message} column stays empty and a call that died during media
     * setup is indistinguishable from one the client simply did not answer.
     */
    @Override
    public void recordError(long callId, String message) {
        if (callId == 0 || message == null || message.isBlank()) {
            return;
        }
        try {
            callAttemptRepository.recordError(callId, message);
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
            callAttemptRepository.updateLanguage(callId, language);
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
    @Override
    public void recordHangupCause(String channelId, String cause) {
        if (channelId == null || cause == null) {
            return;
        }
        try {
            callAttemptRepository.recordHangupCause(channelId, cause);
        } catch (Exception e) {
            log.warn("recordHangupCause failed for channel {}: {}", channelId, e.getMessage());
        }
    }

    /** Full transcript as {@code ROLE: text} lines, in order (for the summary LLM). */
    @Override
    public String findTranscriptText(long callId) {
        if (callId == 0) {
            return "";
        }
        try {
            StringBuilder sb = new StringBuilder();
            for (String line : callTranscriptRepository.findTranscriptLines(callId)) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("findTranscriptText failed for call {}: {}", callId, e.getMessage());
            return "";
        }
    }

    /**
     * §15 "Bugun" mini-statistika — record which panel user answered a transferred call
     * ({@code PbxLegService.handleOperatorJoin}). Best-effort like every other write here.
     */
    public void assignOperator(long callId, long userId) {
        if (callId == 0) {
            return;
        }
        try {
            callAttemptRepository.assignOperator(callId, userId);
        } catch (Exception e) {
            log.warn("assignOperator failed for call {}: {}", callId, e.getMessage());
        }
    }

    /** The call joined a queue (docs/INBOUND-PBX-PLAN.md §11.1); the first queue is the one SLA counts from. */
    @Override
    public void markEnqueued(long callId, long queueId) {
        guarded(callId, "markEnqueued", () -> callAttemptRepository.markEnqueued(callId, queueId, Instant.now()));
    }

    /** An operator answered: who, and how long the caller had waited. */
    @Override
    public void markOperatorAnswered(long callId, long userId, int waitSeconds) {
        guarded(callId, "markOperatorAnswered",
                () -> callAttemptRepository.markOperatorAnswered(callId, userId, Instant.now(), waitSeconds));
    }

    /** The caller gave up waiting. */
    @Override
    public void markAbandoned(long callId, int waitSeconds) {
        guarded(callId, "markAbandoned", () -> callAttemptRepository.markAbandoned(callId, waitSeconds));
    }

    /** The 1-5 rating the caller gave after the operator hung up. */
    @Override
    public void recordSurveyScore(long callId, int score) {
        guarded(callId, "recordSurveyScore", () -> callAttemptRepository.recordSurveyScore(callId, score));
    }

    @Override
    public long countEndedSince(Instant since) {
        return callAttemptRepository.countEndedSince(since);
    }

    @Override
    public long countEndedSinceWithDisposition(Instant since, Disposition disposition) {
        return callAttemptRepository.countEndedSinceWithDisposition(since, disposition);
    }

    /** Who last spoke to this caller as an operator — the sticky routing target (docs/INBOUND-PBX-PLAN.md §9). */
    @Override
    public Long findLastOperator(long companyId, String phone, Instant since) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        try {
            return callAttemptRepository.findLastOperatorUserId(companyId, phone, since);
        } catch (Exception e) {
            log.warn("findLastOperator failed for {}: {}", PhoneMasker.mask(phone), e.getMessage());
            return null;
        }
    }

    private static void guarded(long callId, String what, Runnable write) {
        if (callId == 0) {
            return;
        }
        try {
            write.run();
        } catch (Exception e) {
            log.warn("{} failed for call {}: {}", what, callId, e.getMessage());
        }
    }

    /** Close out a call_attempt with its outcome. */
    @Override
    public void finishAttempt(long callId, Disposition disposition, Long recordingFileId, int durationSec) {
        seqCounters.remove(callId);
        if (callId == 0) {
            return;
        }
        try {
            callAttemptRepository.finishAttempt(callId, Instant.now(), durationSec, disposition, recordingFileId);
        } catch (Exception e) {
            log.warn("finishAttempt failed for call {}: {}", callId, e.getMessage());
        }
    }

    /**
     * The outcome alone, for one a later summary found that the call's close did not — the
     * outbox's payment promise. Best effort, like the other writes here.
     */
    public void updateDisposition(long callId, Disposition disposition) {
        if (callId == 0 || disposition == null) {
            return;
        }
        try {
            callAttemptRepository.updateDisposition(callId, disposition);
        } catch (Exception e) {
            log.warn("updateDisposition failed for call {}: {}", callId, e.getMessage());
        }
    }

    /**
     * Insert the single call_result row (§4.3), and queue the call's CRM note with it.
     *
     * <p>One transaction: a result is never stored without its note queued, and a note is
     * never queued for a result that was not stored. Idempotent on both: the outbox re-runs
     * the summary for calls that have no result yet, and two instances doing so at once would
     * otherwise have one of them fail on the unique {@code call_id}. Whichever writes first
     * wins — they are summarizing the same transcript. Unlike the other writes here this one
     * is not fail-open: a failure rolls both back and reaches the caller.
     *
     * @param crmNote the note to queue, or null when the call has no CRM lead to file one under
     * @return the id of the note queued by this call, or null when none was
     */
    @Transactional
    public Long writeResult(long companyId, long callId, CallSummary s, boolean escalated, OutboxMessage crmNote) {
        if (callId == 0 || s == null) {
            return null;
        }
        callResultRepository.insertIgnoringConflict(CallResult.summaryOf(
                callId,
                s.summary() != null ? s.summary() : "",
                asReasonCode(s.outcome().get("reasonCode")),
                asDate(s.outcome().get("promisedDate")),
                asAmount(s.outcome().get("promisedAmount")),
                s.sentiment(),
                s.needsFollowUp(),
                s.followUpNote(),
                escalated,
                null,
                toJson(s.outcome())));
        log.info("call_result written for call {} (disposition-escalated={})", callId, escalated);
        return crmNote != null ? outboxMessageRepository.enqueue(companyId, crmNote) : null;
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
                                     String llmModel, DialogTechnicalSnapshot technical,
                                     RtpQuality mediaQuality) {
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
            if (mediaQuality != null) {
                row = row.withMediaQuality((int) mediaQuality.packetsReceived(), (int) mediaQuality.packetsLost(),
                        mediaQuality.jitterMs(), mediaQuality.mosEstimate());
            }
            callTechnicalRepository.save(row);
        } catch (Exception e) {
            log.warn("writeTechnicalDetail failed for call {}: {}", callId, e.getMessage());
        }
    }
}
