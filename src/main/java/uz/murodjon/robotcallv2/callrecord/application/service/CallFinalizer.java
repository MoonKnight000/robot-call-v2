package uz.murodjon.robotcallv2.callrecord.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.dialog.CallSummary;
import uz.murodjon.robotcallv2.agent.dialog.DialogTechnicalSnapshot;
import uz.murodjon.robotcallv2.agent.metrics.VoiceMetrics;
import uz.murodjon.robotcallv2.agent.rtp.RtpQuality;
import uz.murodjon.robotcallv2.agent.stt.SttProperties;
import uz.murodjon.robotcallv2.agent.summary.SummarizedCall;
import uz.murodjon.robotcallv2.agent.summary.SummaryService;
import uz.murodjon.robotcallv2.agent.tts.TtsProperties;
import uz.murodjon.robotcallv2.agent.vad.VadProperties;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.billing.domain.entity.CallUsage;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallFinalizationUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.output.RecordingStoragePort;
import uz.murodjon.robotcallv2.callrecord.domain.entity.AnsweredCall;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallAttempt;
import uz.murodjon.robotcallv2.callrecord.domain.entity.OutboxMessage;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmCallHistoryEntry;
import uz.murodjon.robotcallv2.inbound.application.port.input.InboundRouteUseCase;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRoute;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.quality.application.port.input.CallQualityUseCase;
import uz.murodjon.robotcallv2.quality.domain.entity.FinishedCall;
import uz.murodjon.robotcallv2.scenario.application.port.input.ScenarioUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.dialog.Sentiment;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;
import uz.murodjon.robotcallv2.voice.application.port.input.TtsVoiceUseCase;
import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;

import java.nio.file.Path;
import java.time.*;
import java.util.List;

/**
 * Runs the post-call pipeline once a call ends (PROJECT.md §4.3, Stage 9).
 */
@Component
public class CallFinalizer implements CallFinalizationUseCase {

    private static final Logger log = LoggerFactory.getLogger(CallFinalizer.class);

    /** A caller who rings again within this is taken to be back with the same matter (UNIVERSAL-OPERATOR-PLAN D.1). */
    private static final Duration REPEAT_CALL_WINDOW = Duration.ofHours(24);

    private final CallRecordService callRecordService;
    private final SummaryService summaryService;
    private final CallQualityUseCase callQualityUseCase;
    private final RecordingStoragePort recordingStoragePort;
    /** Where the CRM note and call history go: queued, then delivered from the outbox. */
    private final OutboxDeliveryService outboxDeliveryService;
    private final ScenarioUseCase scenarioUseCase;
    private final CampaignTargetUseCase campaignTargetUseCase;
    private final NotificationUseCase notificationUseCase;
    private final VoiceMetrics metrics;
    private final SttProperties sttProperties;
    private final TtsProperties ttsProperties;
    private final TtsVoiceUseCase ttsVoiceUseCase;
    private final VadProperties vadProperties;
    private final CallMemoryWriter memoryWriter;
    private final PostCallActionExecutor postCallActionExecutor;
    private final CallBillingUseCase callBilling;
    /** Where an inbound caller's callback campaign comes from. */
    private final InboundRouteUseCase inboundRouteUseCase;
    /** Whose clock a "call me back at five" is read in. */
    private final CompanyConfigUseCase companyConfigUseCase;
    private final String llmModel;

    public CallFinalizer(CallRecordService callRecordService, SummaryService summaryService,
                         CallQualityUseCase callQualityUseCase,
                         RecordingStoragePort recordingStoragePort, OutboxDeliveryService outboxDeliveryService,
                         ScenarioUseCase scenarioUseCase,
                         CampaignTargetUseCase campaignTargetUseCase, NotificationUseCase notificationUseCase,
                         VoiceMetrics metrics, SttProperties sttProperties, TtsProperties ttsProperties,
                         TtsVoiceUseCase ttsVoiceUseCase, VadProperties vadProperties, CallMemoryWriter memoryWriter,
                         PostCallActionExecutor postCallActionExecutor, CallBillingUseCase callBilling,
                         InboundRouteUseCase inboundRouteUseCase, CompanyConfigUseCase companyConfigUseCase,
                         @Value("${spring.ai.google.genai.chat.options.model:}") String llmModel) {
        this.inboundRouteUseCase = inboundRouteUseCase;
        this.companyConfigUseCase = companyConfigUseCase;
        this.callRecordService = callRecordService;
        this.summaryService = summaryService;
        this.callQualityUseCase = callQualityUseCase;
        this.recordingStoragePort = recordingStoragePort;
        this.outboxDeliveryService = outboxDeliveryService;
        this.scenarioUseCase = scenarioUseCase;
        this.campaignTargetUseCase = campaignTargetUseCase;
        this.notificationUseCase = notificationUseCase;
        this.metrics = metrics;
        this.sttProperties = sttProperties;
        this.ttsProperties = ttsProperties;
        this.ttsVoiceUseCase = ttsVoiceUseCase;
        this.vadProperties = vadProperties;
        this.memoryWriter = memoryWriter;
        this.postCallActionExecutor = postCallActionExecutor;
        this.callBilling = callBilling;
        this.llmModel = llmModel;
    }

    /**
     * @return the disposition the call actually settled on — the one passed in, unless
     *         the summary found an outcome the dialog never got to record
     *         ({@link #promised}). The caller applies it to the campaign target, so this
     *         has to be the final word rather than the one the hangup produced.
     */
    @Override
    public Disposition finalizeCall(long callAttemptId, long clientId, long scenarioId, Path wav, Instant startedAt,
                                    Disposition disposition, String channelName, String trunk,
                                    DialogTechnicalSnapshot technical,
                                    AiAgent agent,
                                    String phone,
                                    RtpQuality mediaQuality) {
        if (callAttemptId == 0) {
            return disposition;
        }
        try {
            int durationSec = (int) Duration.between(startedAt, Instant.now()).getSeconds();
            boolean escalated = disposition == Disposition.TRANSFERRED;
            metrics.recordCallDuration(durationSec);

            long companyId = 0L;
            try {
                companyId = callRecordService.findCompanyIdByAttemptId(callAttemptId);
            } catch (Exception e) {
                log.warn("[{}] could not resolve companyId: {}", callAttemptId, e.getMessage());
            }

            Long recordingFileId = null;
            try {
                recordingFileId = recordingStoragePort.uploadRecording(wav, companyId, wav.getFileName().toString());
                if (recordingFileId != null) {
                    recordingStoragePort.deleteLocalCopy(wav);
                }
            } catch (Exception e) {
                log.warn("[{}] audio upload to storage failed, continuing call finalization: {}", callAttemptId, e.getMessage());
            }

            try {
                callRecordService.finishAttempt(callAttemptId, disposition, recordingFileId, durationSec);
            } catch (Exception e) {
                log.error("[{}] initial finishAttempt failed: {}", callAttemptId, e.getMessage());
            }

            SummarizedCall summarized = null;
            CallSummary summary = null;
            Scenario scenarioRow = null;
            String transcript = null;
            ScenarioDefinition scenario = null;
            if (scenarioId == 0) {
                // No AI agent ran this call — an IVR, a queue, an operator. There is no scenario
                // to summarise or score it against.
                metrics.disposition(disposition);
            } else {
                try {
                    transcript = callRecordService.findTranscriptText(callAttemptId);
                    scenarioRow = scenarioUseCase.requireScenario(companyId, scenarioId);
                    scenario = scenarioRow.definition();
                    // The agent is what asks for dataNeeded / dataEvaluation; without it those were never extracted.
                    summarized = summaryService.summarize(transcript, scenario, agent);
                    summary = summarized != null ? summarized.summary() : null;
                    if (disposition == null && promised(summary)) {
                        disposition = Disposition.PROMISE_TO_PAY;
                        callRecordService.finishAttempt(callAttemptId, disposition, recordingFileId, durationSec);
                        log.info("Call {} left no disposition; summary found a payment promise -> {}",
                                callAttemptId, disposition);
                    }
                    metrics.disposition(disposition);
                } catch (Exception e) {
                    log.warn("[{}] summary failed: {}", callAttemptId, e.getMessage());
                }
            }
            if (scenario != null) {
                recordQuality(companyId, callAttemptId, agent, disposition, transcript, scenario, technical);
            }

            // Before the summary, and outside the block below: the conversation happened
            // whether or not an LLM managed to summarise it, and a missing call-history row
            // is a gap in the CRM's own reporting. recordUrl is deliberately not sent —
            // Uysot downloads that link itself, and the recording lives in a MinIO bucket
            // that is not reachable from outside this deployment. Queued in the outbox and
            // delivered from there, so a CRM that is down gets it on a retry.
            try {
                outboxDeliveryService.enqueueCallHistory(companyId, callAttemptId, new CrmCallHistoryEntry(clientId,
                        "voice-" + callAttemptId, startedAt, durationSec, callRecordService.isInbound(callAttemptId),
                        wasAnswered(disposition), phone, null));
            } catch (Exception e) {
                log.warn("[{}] CRM call history not queued: {}", callAttemptId, e.getMessage());
            }

            if (summary != null) {
                applySummary(companyId, callAttemptId, clientId, phone, agent, scenarioRow, disposition, summary,
                        escalated);

                log.info("Finalized call {} (dur={}s, disposition={}, sentiment={})",
                        callAttemptId, durationSec, disposition, summary.sentiment());
            } else {
                log.info("Finalized call {} without summary (LLM unavailable or empty transcript)", callAttemptId);
            }

            try {
                writeTechnicalDetail(callAttemptId, companyId, disposition, channelName, trunk, technical, agent,
                        mediaQuality);
            } catch (Exception e) {
                log.warn("[{}] writeTechnicalDetail failed: {}", callAttemptId, e.getMessage());
            }

            settle(callAttemptId, companyId, durationSec, technical);
            chargeSummary(companyId, callAttemptId, summarized);
        } catch (Exception e) {
            log.warn("Finalization failed for call {}: {}", callAttemptId, e.getMessage());
        }
        return disposition;
    }

    /**
     * Everything a summary sets off — the CRM note, the stored result, the client's memory,
     * a callback the caller asked for, the hostile-caller alert and the agent's post-call
     * actions. The same on the first pass and on the outbox's retry
     * ({@link CallOutboxService}): the retry used to write the result and the note only, so a
     * callback or a post-call webhook asked for on a call whose first summary failed never
     * happened.
     */
    void applySummary(long companyId, long callAttemptId, long clientId, String phone, AiAgent agent,
                      Scenario scenarioRow, Disposition disposition, CallSummary summary, boolean escalated) {
        // A zero-PII agent's call keeps its outcome, not its words: that is what is stored
        // here — the result, the client's memory (a summary with no text leaves none), the
        // alert. The company's own CRM and post-call actions still get the whole summary;
        // they are where it was meant to go.
        CallSummary stored = agent != null && agent.dataPolicy().zeroPiiRetention()
                ? summary.stripPersonalData(goalFieldOf(scenarioRow)) : summary;

        // The note is queued with the result, in one transaction, and delivered from the outbox
        // right after: a result never exists without its note queued, and a note the CRM
        // refuses now is posted on a retry. A call with no CRM lead gets no note at all.
        OutboxMessage crmNote = outboxDeliveryService.prepareCrmNote(companyId, callAttemptId, clientId, summary);
        Long crmNoteMessageId = null;
        try {
            crmNoteMessageId = callRecordService.writeResult(companyId, callAttemptId, stored, escalated, crmNote);
        } catch (Exception e) {
            log.error("[{}] writeResult failed: {}", callAttemptId, e.getMessage());
        }
        if (crmNoteMessageId != null) {
            outboxDeliveryService.deliverNow(crmNoteMessageId);
        }

        memoryWriter.remember(callAttemptId, companyId, scenarioRow, disposition, stored);

        try {
            if (summary.callbackAt() != null && !summary.callbackAt().isBlank()) {
                Instant callbackInstant = parseCallbackInstant(companyId, summary.callbackAt());
                if (callbackInstant != null && callbackInstant.isAfter(Instant.now())) {
                    // An inbound call has no campaign target to reschedule; the caller joins
                    // the route's callback campaign instead.
                    if (callRecordService.isInbound(callAttemptId)) {
                        queueInboundCallback(companyId, callAttemptId, clientId, callbackInstant);
                    } else {
                        long targetId = callRecordService.findTargetIdByAttemptId(callAttemptId);
                        if (targetId != CallAttempt.NO_TARGET) {
                            campaignTargetUseCase.scheduleCallback(companyId, targetId, callbackInstant);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[{}] callback schedule failed: {}", callAttemptId, e.getMessage());
        }

        try {
            if (summary.sentiment() == Sentiment.HOSTILE) {
                notificationUseCase.notify(companyId, NotificationType.OPERATOR_REQUEST,
                        "Salbiy muloqot aniqlandi",
                        "Qo'ng'iroqda (ID: " + callAttemptId + ") mijoz keskin norozilik bildirdi: " + stored.summary(),
                        null);
            }
        } catch (Exception e) {
            log.warn("[{}] hostile notification failed: {}", callAttemptId, e.getMessage());
        }

        if (agent != null) {
            try {
                postCallActionExecutor.executeActions(companyId, callAttemptId, phone, agent, disposition, summary);
            } catch (Exception e) {
                log.warn("[{}] post-call action execution failed: {}", callAttemptId, e.getMessage());
            }
        }
    }

    /**
     * The call's quality row (UNIVERSAL-OPERATOR-PLAN D.1). The same number's earlier calls are
     * found here, where the calls are, and handed over to be marked as repeated.
     */
    private void recordQuality(long companyId, long callAttemptId, AiAgent agent, Disposition disposition,
                               String transcript, ScenarioDefinition scenario, DialogTechnicalSnapshot technical) {
        try {
            callQualityUseCase.recordCallQuality(companyId, new FinishedCall(callAttemptId,
                    agent != null ? agent.id() : null,
                    agent != null ? agent.publishedVersion() : null,
                    disposition,
                    transcript,
                    technical != null ? technical.turnCount() : 0,
                    technical != null ? technical.bargeInCount() : 0,
                    technical != null ? technical.noInputPromptCount() : 0,
                    technical != null ? technical.lowConfidenceCount() : 0,
                    agent != null && agent.dataPolicy().zeroPiiRetention(),
                    findEarlierCallIds(companyId, callAttemptId)), scenario);
        } catch (Exception e) {
            log.warn("[{}] quality record failed: {}", callAttemptId, e.getMessage());
        }
    }

    /** The same number's answered calls of the last day, this one left out; none for a withheld number. */
    private List<Long> findEarlierCallIds(long companyId, long callAttemptId) {
        String phone = callRecordService.findPhoneByAttemptId(callAttemptId);
        if (phone == null || !PhoneNumbers.isValid(phone)) {
            return List.of();
        }
        Instant now = Instant.now();
        return callRecordService.findAnsweredCalls(companyId, phone, now.minus(REPEAT_CALL_WINDOW), now).stream()
                .map(AnsweredCall::callAttemptId)
                .filter(id -> id != callAttemptId)
                .toList();
    }

    /**
     * Charges the call for what it consumed and gives back whatever the dialer held for it.
     *
     * <p>Last, after the record is written: a company should be able to see the call it
     * was charged for, and a charge without its call is the harder thing to explain. The
     * target is looked up rather than carried down here because that is what pairs the
     * charge with the hold taken before the number was dialled.
     */
    private void settle(long callAttemptId, long companyId, int durationSec, DialogTechnicalSnapshot technical) {
        try {
            // Inbound and manual calls have no target (0) and the dialer never held money
            // for them, so they are settled as unheld calls.
            long targetId = callRecordService.findTargetIdByAttemptId(callAttemptId);
            DialogTechnicalSnapshot counted = technical != null ? technical : DialogTechnicalSnapshot.NONE;
            // A call that ran on a speech-to-speech engine is priced on its minutes; its
            // tokens are recorded but not priced (CallCostCalculator).
            int realtimeSeconds = counted.engine() != null ? durationSec : 0;
            callBilling.settleCall(companyId, callAttemptId, targetId > 0 ? targetId : null,
                    new CallUsage(durationSec, counted.promptTokens(), counted.completionTokens(),
                            counted.cachedTokens(), counted.ttsChars(), realtimeSeconds));
        } catch (Exception e) {
            log.warn("[{}] billing settlement failed: {}", callAttemptId, e.getMessage());
        }
    }

    /**
     * Charges the post-call summary's tokens to the call, after its settlement — on the
     * first pass and on the outbox's retry alike ({@link CallBillingUseCase#chargeSummary}).
     * Best effort, like the settlement: the call is over and recorded either way.
     */
    void chargeSummary(long companyId, long callAttemptId, SummarizedCall summarized) {
        if (summarized == null) {
            return;
        }
        try {
            callBilling.chargeSummary(companyId, callAttemptId, new CallUsage(0, summarized.promptTokens(),
                    summarized.completionTokens(), summarized.cachedTokens(), 0));
        } catch (Exception e) {
            log.warn("[{}] summary billing failed: {}", callAttemptId, e.getMessage());
        }
    }

    /**
     * Whether anything picked the call up, for the CRM's call history. Derived from the
     * disposition rather than {@code answered_at} because a call that ends before the
     * dialog starts still has to be filed, and the three dispositions below are the only
     * ones that mean nobody was ever on the line. A voicemail counts as answered: the
     * carrier connected the call, and the CRM's own reporting counts it that way too.
     */
    private static boolean wasAnswered(Disposition disposition) {
        return disposition != Disposition.NO_ANSWER
                && disposition != Disposition.CARRIER_REJECTED
                && disposition != Disposition.FAILED;
    }

    /** Whether the summary read a payment date off the conversation. */
    static boolean promised(CallSummary summary) {
        if (summary == null) {
            return false;
        }
        Object promisedDate = summary.outcome().get("promisedDate");
        return promisedDate != null && !promisedDate.toString().isBlank();
    }

    /**
     * Put an inbound caller who asked to be called back on their route's callback campaign,
     * due at the time they named. A route with no callback campaign keeps the old
     * behaviour: the request stays on the call's outcome and nothing is dialled.
     */
    private void queueInboundCallback(long companyId, long callAttemptId, long clientId, Instant callbackAt) {
        Long routeId = callRecordService.findInboundRouteIdByAttemptId(callAttemptId);
        InboundRoute route = routeId != null ? inboundRouteUseCase.requireRoute(companyId, routeId) : null;
        if (route == null || route.callbackCampaignId() == null) {
            log.info("[{}] caller asked for a callback at {}, but the route has no callback campaign — recorded only",
                    callAttemptId, callbackAt);
            return;
        }
        String phone = callRecordService.findPhoneByAttemptId(callAttemptId);
        if (phone == null || phone.isBlank()) {
            return;
        }
        long targetId = campaignTargetUseCase.addTarget(companyId, route.callbackCampaignId(), clientId, phone, null, null);
        campaignTargetUseCase.scheduleCallback(companyId, targetId, callbackAt);
        log.info("[{}] inbound caller queued on campaign {} for a callback at {} (target {})",
                callAttemptId, route.callbackCampaignId(), callbackAt, targetId);
    }

    private Instant parseCallbackInstant(long companyId, String callbackAt) {
        if (callbackAt == null || callbackAt.isBlank()) {
            return null;
        }
        try {
            CompanyConfig config = companyConfigUseCase.find(companyId);
            ZoneId zone = config != null ? config.zoneId() : ZoneId.systemDefault();
            if (callbackAt.contains("T")) {
                LocalDateTime ldt = LocalDateTime.parse(callbackAt);
                return ldt.atZone(zone).toInstant();
            } else {
                LocalDate ld = LocalDate.parse(callbackAt);
                return ld.atTime(10, 0).atZone(zone).toInstant();
            }
        } catch (Exception e) {
            log.warn("Could not parse callbackAt '{}': {}", callbackAt, e.getMessage());
            return null;
        }
    }

    /**
     * Resolves the bits of the "Texnik" tab (§10.5) that do not need to be captured
     * live — STT/TTS provider, LLM model, AMD result — and persists everything
     * together with what {@link DialogTechnicalSnapshot} already accumulated.
     *
     * <p>What ran the call decides all of it. On a REALTIME call one vendor did the
     * recognition, the reasoning and the speech, so it is named as all three: the agent's
     * cascade providers and the deployment's chat model were never touched, and filing the
     * call under them is how a Gemini Live call came to read as Yandex.
     */
    private void writeTechnicalDetail(long callAttemptId, long companyId, Disposition disposition, String channelName,
                                      String trunk, DialogTechnicalSnapshot technical, AiAgent agent,
                                      RtpQuality mediaQuality) {
        String amdResult = amdResult(disposition);
        String engine = technical != null ? technical.engine() : null;
        String agentStt = agent != null ? agent.speechEngine().sttProvider() : null;
        String agentTts = agent != null ? agent.speechEngine().ttsProvider() : null;
        String sttProvider = engine != null ? engine : chooseNonBlank(agentStt, sttProperties.provider());
        String ttsProvider = engine != null ? engine : chooseNonBlank(agentTts, ttsProperties.provider());
        String ttsVoiceName = null;
        if (technical != null && technical.ttsVoice() != null) {
            TtsVoice voice = ttsVoiceUseCase.find(technical.ttsVoice());
            if (voice != null) {
                ttsProvider = voice.provider();
                ttsVoiceName = voice.name();
            }
        }
        String model = chooseNonBlank(technical != null ? technical.llmModel() : null, llmModel);
        callRecordService.writeTechnicalDetail(callAttemptId, channelName, trunk, amdResult,
                sttProvider, ttsProvider, ttsVoiceName,
                model == null || model.isBlank() ? null : model, technical, mediaQuality);
    }

    /** {@code preferred} when it says something, otherwise {@code fallback}. */
    private static String chooseNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    /** The one outcome field a zero-PII call still keeps — what it was made for (E.3). */
    private static String goalFieldOf(Scenario scenario) {
        return scenario != null && scenario.definition() != null ? scenario.definition().findGoalField() : null;
    }

    /**
     * What answering-machine detection concluded — null when it never had a chance to:
     * a call nobody picked up has no audio to classify, and filing it as HUMAN would put
     * every NO_ANSWER into the "reached a person" column.
     */
    private String amdResult(Disposition d) {
        if (!wasAnswered(d)) {
            return null;
        }
        if (d == Disposition.VOICEMAIL) {
            return "VOICEMAIL";
        }
        if (vadProperties.amd() == null || !vadProperties.amd().enabled()) {
            return "DISABLED";
        }
        return "HUMAN";
    }
}
