package uz.murodjon.robotcallv2.callrecord.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.dialog.CallSummary;
import uz.murodjon.robotcallv2.agent.dialog.DialogTechnicalSnapshot;
import uz.murodjon.robotcallv2.agent.metrics.VoiceMetrics;
import uz.murodjon.robotcallv2.agent.stt.SttProperties;
import uz.murodjon.robotcallv2.agent.summary.CallQualityJudge;
import uz.murodjon.robotcallv2.agent.summary.SummaryService;
import uz.murodjon.robotcallv2.agent.tts.TtsProperties;
import uz.murodjon.robotcallv2.agent.vad.VadProperties;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.billing.domain.entity.CallUsage;
import uz.murodjon.robotcallv2.campaign.application.service.CampaignService;
import uz.murodjon.robotcallv2.crm.application.service.CrmClient;
import uz.murodjon.robotcallv2.notification.application.service.NotificationService;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.scenario.application.service.ScenarioService;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.dialog.Sentiment;
import uz.murodjon.robotcallv2.storage.application.service.AudioStorageService;
import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;
import uz.murodjon.robotcallv2.voice.application.service.TtsVoiceService;
import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;

import java.nio.file.Path;
import java.time.*;

/**
 * Runs the post-call pipeline once a call ends (PROJECT.md §4.3, Stage 9).
 */
@Component
public class CallFinalizer {

    private static final Logger log = LoggerFactory.getLogger(CallFinalizer.class);

    private final CallRecordService records;
    private final SummaryService summaryService;
    private final CallQualityJudge qualityJudge;
    private final AudioStorageService storage;
    private final CrmClient crmClient;
    private final ScenarioService scenarioService;
    private final CampaignService campaignService;
    private final NotificationService notificationService;
    private final VoiceMetrics metrics;
    private final SttProperties sttProperties;
    private final TtsProperties ttsProperties;
    private final TtsVoiceService voices;
    private final VadProperties vadProperties;
    private final CallMemoryWriter memoryWriter;
    private final PostCallActionExecutor postCallActionExecutor;
    private final CallBillingUseCase callBilling;
    private final String llmModel;

    public CallFinalizer(CallRecordService records, SummaryService summaryService, CallQualityJudge qualityJudge,
                         AudioStorageService storage, CrmClient crmClient, ScenarioService scenarioService,
                         CampaignService campaignService, NotificationService notificationService,
                         VoiceMetrics metrics, SttProperties sttProperties, TtsProperties ttsProperties,
                         TtsVoiceService voices, VadProperties vadProperties, CallMemoryWriter memoryWriter,
                         PostCallActionExecutor postCallActionExecutor, CallBillingUseCase callBilling,
                         @Value("${spring.ai.google.genai.chat.options.model:}") String llmModel) {
        this.records = records;
        this.summaryService = summaryService;
        this.qualityJudge = qualityJudge;
        this.storage = storage;
        this.crmClient = crmClient;
        this.scenarioService = scenarioService;
        this.campaignService = campaignService;
        this.notificationService = notificationService;
        this.metrics = metrics;
        this.sttProperties = sttProperties;
        this.ttsProperties = ttsProperties;
        this.voices = voices;
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
    public Disposition finalizeCall(long callAttemptId, long clientId, long scenarioId, Path wav, Instant startedAt,
                                    Disposition disposition, String channelName, String trunk,
                                    DialogTechnicalSnapshot technical) {
        return finalizeCall(callAttemptId, clientId, scenarioId, wav, startedAt, disposition, channelName, trunk, technical, null, null);
    }

    public Disposition finalizeCall(long callAttemptId, long clientId, long scenarioId, Path wav, Instant startedAt,
                                    Disposition disposition, String channelName, String trunk,
                                    DialogTechnicalSnapshot technical,
                                    AiAgent agent,
                                    String phone) {
        if (callAttemptId == 0) {
            return disposition;
        }
        try {
            int durationSec = (int) Duration.between(startedAt, Instant.now()).getSeconds();
            boolean escalated = disposition == Disposition.TRANSFERRED;
            metrics.recordCallDuration(durationSec);

            long companyId = 0L;
            try {
                companyId = records.companyIdOf(callAttemptId);
            } catch (Exception e) {
                log.warn("[{}] could not resolve companyId: {}", callAttemptId, e.getMessage());
            }

            StoredFile stored = null;
            Long recordingFileId = null;
            try {
                stored = storage.upload(wav, companyId, wav.getFileName().toString());
                if (stored != null) {
                    recordingFileId = stored.id();
                    storage.deleteLocalCopy(wav);
                }
            } catch (Exception e) {
                log.warn("[{}] audio upload to storage failed, continuing call finalization: {}", callAttemptId, e.getMessage());
            }

            try {
                records.finishAttempt(callAttemptId, disposition, recordingFileId, durationSec);
            } catch (Exception e) {
                log.error("[{}] initial finishAttempt failed: {}", callAttemptId, e.getMessage());
            }

            CallSummary summary = null;
            Scenario scenarioRow = null;
            try {
                String transcript = records.transcriptText(callAttemptId);
                scenarioRow = scenarioService.requireScenario(companyId, scenarioId);
                ScenarioDefinition scenario = scenarioRow.definition();
                summary = summaryService.summarize(transcript, scenario);
                if (disposition == null && promised(summary)) {
                    disposition = Disposition.PROMISE_TO_PAY;
                    records.finishAttempt(callAttemptId, disposition, recordingFileId, durationSec);
                    log.info("Call {} left no disposition; summary found a payment promise -> {}",
                            callAttemptId, disposition);
                }
                metrics.disposition(disposition);
                qualityJudge.judge(transcript, scenario, disposition);
            } catch (Exception e) {
                log.warn("[{}] summary or quality scoring failed: {}", callAttemptId, e.getMessage());
            }

            // Before the summary, and outside the block below: the conversation happened
            // whether or not an LLM managed to summarise it, and a missing call-history row
            // is a gap in the CRM's own reporting. recordUrl is deliberately not sent —
            // Uysot downloads that link itself, and the recording lives in a MinIO bucket
            // that is not reachable from outside this deployment.
            try {
                crmClient.postCallHistory(companyId, clientId, "voice-" + callAttemptId, startedAt, durationSec,
                        records.isInbound(callAttemptId), wasAnswered(disposition), phone, null);
            } catch (Exception e) {
                log.warn("[{}] CRM call history failed: {}", callAttemptId, e.getMessage());
            }

            if (summary != null) {
                String crmNoteId = null;
                try {
                    crmNoteId = crmClient.postNote(companyId, clientId, summary);
                } catch (Exception e) {
                    log.warn("[{}] CRM postNote failed (call record still saved): {}", callAttemptId, e.getMessage());
                }

                try {
                    records.writeResult(callAttemptId, summary, escalated, crmNoteId);
                } catch (Exception e) {
                    log.error("[{}] writeResult failed: {}", callAttemptId, e.getMessage());
                }

                memoryWriter.remember(callAttemptId, companyId, scenarioRow, disposition, summary);

                try {
                    if (summary.callbackAt() != null && !summary.callbackAt().isBlank()) {
                        long targetId = records.targetIdOf(callAttemptId);
                        if (targetId != 0L) {
                            Instant callbackInstant = parseCallbackInstant(summary.callbackAt());
                            if (callbackInstant != null && callbackInstant.isAfter(Instant.now())) {
                                campaignService.scheduleCallback(companyId, targetId, callbackInstant);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("[{}] callback schedule failed: {}", callAttemptId, e.getMessage());
                }

                try {
                    if (summary.sentiment() == Sentiment.HOSTILE) {
                        notificationService.notify(companyId, NotificationType.OPERATOR_REQUEST,
                                "Salbiy muloqot aniqlandi",
                                "Qo'ng'iroqda (ID: " + callAttemptId + ") mijoz keskin norozilik bildirdi: " + summary.summary(),
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

                log.info("Finalized call {} (dur={}s, disposition={}, sentiment={})",
                        callAttemptId, durationSec, disposition, summary.sentiment());
            } else {
                log.info("Finalized call {} without summary (LLM unavailable or empty transcript)", callAttemptId);
            }

            try {
                writeTechnicalDetail(callAttemptId, companyId, disposition, channelName, trunk, technical, agent);
            } catch (Exception e) {
                log.warn("[{}] writeTechnicalDetail failed: {}", callAttemptId, e.getMessage());
            }

            settle(callAttemptId, companyId, durationSec, technical);
        } catch (Exception e) {
            log.warn("Finalization failed for call {}: {}", callAttemptId, e.getMessage());
        }
        return disposition;
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
            // Inbound and manual calls sit on a shared synthetic target that the dialer
            // never holds money against, so looking one up for them simply finds nothing.
            long targetId = records.targetIdOf(callAttemptId);
            DialogTechnicalSnapshot counted = technical != null ? technical : DialogTechnicalSnapshot.NONE;
            callBilling.settleCall(companyId, callAttemptId, targetId > 0 ? targetId : null,
                    new CallUsage(durationSec, counted.promptTokens(), counted.completionTokens(),
                            counted.cachedTokens(), counted.ttsChars()));
        } catch (Exception e) {
            log.warn("[{}] billing settlement failed: {}", callAttemptId, e.getMessage());
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
    private static boolean promised(CallSummary summary) {
        if (summary == null) {
            return false;
        }
        Object promisedDate = summary.outcome().get("promisedDate");
        return promisedDate != null && !promisedDate.toString().isBlank();
    }

    private Instant parseCallbackInstant(String callbackAt) {
        if (callbackAt == null || callbackAt.isBlank()) {
            return null;
        }
        try {
            if (callbackAt.contains("T")) {
                LocalDateTime ldt = LocalDateTime.parse(callbackAt);
                return ldt.atZone(ZoneId.of("Asia/Tashkent")).toInstant();
            } else {
                LocalDate ld = LocalDate.parse(callbackAt);
                return ld.atTime(10, 0).atZone(ZoneId.of("Asia/Tashkent")).toInstant();
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
                                      String trunk, DialogTechnicalSnapshot technical, AiAgent agent) {
        String amdResult = amdResult(disposition);
        String engine = technical != null ? technical.engine() : null;
        String agentStt = agent != null ? agent.speechEngine().sttProvider() : null;
        String agentTts = agent != null ? agent.speechEngine().ttsProvider() : null;
        String sttProvider = engine != null ? engine : chooseNonBlank(agentStt, sttProperties.provider());
        String ttsProvider = engine != null ? engine : chooseNonBlank(agentTts, ttsProperties.provider());
        String ttsVoiceName = null;
        if (technical != null && technical.ttsVoice() != null) {
            TtsVoice voice = voices.find(technical.ttsVoice());
            if (voice != null) {
                ttsProvider = voice.provider();
                ttsVoiceName = voice.name();
            }
        }
        String model = chooseNonBlank(technical != null ? technical.llmModel() : null, llmModel);
        records.writeTechnicalDetail(callAttemptId, channelName, trunk, amdResult,
                sttProvider, ttsProvider, ttsVoiceName,
                model == null || model.isBlank() ? null : model, technical);
    }

    /** {@code preferred} when it says something, otherwise {@code fallback}. */
    private static String chooseNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    private String amdResult(Disposition d) {
        if (d == Disposition.VOICEMAIL) {
            return "VOICEMAIL";
        }
        if (vadProperties.amd() == null || !vadProperties.amd().enabled()) {
            return "DISABLED";
        }
        return "HUMAN";
    }
}
