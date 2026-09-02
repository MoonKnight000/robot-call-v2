package uz.murodjon.robotcallv2.callrecord.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.dialog.CallSummary;
import uz.murodjon.robotcallv2.agent.dialog.DialogTechnicalSnapshot;
import uz.murodjon.robotcallv2.agent.metrics.VoiceMetrics;
import uz.murodjon.robotcallv2.agent.summary.SummaryService;
import uz.murodjon.robotcallv2.agent.vad.VadProperties;
import uz.murodjon.robotcallv2.campaign.application.service.CampaignService;
import uz.murodjon.robotcallv2.crm.application.service.CrmClient;
import uz.murodjon.robotcallv2.engine.application.service.EngineConfigService;
import uz.murodjon.robotcallv2.engine.domain.entity.EffectiveEngineConfig;
import uz.murodjon.robotcallv2.notification.application.service.NotificationService;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.scenario.application.service.ScenarioService;
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
    private final AudioStorageService storage;
    private final CrmClient crmClient;
    private final ScenarioService scenarioService;
    private final CampaignService campaignService;
    private final NotificationService notificationService;
    private final VoiceMetrics metrics;
    private final EngineConfigService engineConfigService;
    private final TtsVoiceService voices;
    private final VadProperties vadProps;
    private final String llmModel;

    public CallFinalizer(CallRecordService records, SummaryService summaryService,
                         AudioStorageService storage, CrmClient crmClient, ScenarioService scenarioService,
                         CampaignService campaignService, NotificationService notificationService,
                         VoiceMetrics metrics, EngineConfigService engineConfigService, TtsVoiceService voices,
                         VadProperties vadProps,
                         @Value("${spring.ai.google.genai.chat.options.model:}") String llmModel) {
        this.records = records;
        this.summaryService = summaryService;
        this.storage = storage;
        this.crmClient = crmClient;
        this.scenarioService = scenarioService;
        this.campaignService = campaignService;
        this.notificationService = notificationService;
        this.metrics = metrics;
        this.engineConfigService = engineConfigService;
        this.voices = voices;
        this.vadProps = vadProps;
        this.llmModel = llmModel;
    }

    public void finalizeCall(long callAttemptId, long clientId, long scenarioId, Path wav, Instant startedAt,
                             Disposition disposition, String channelName, String trunk,
                             DialogTechnicalSnapshot technical) {
        if (callAttemptId == 0) {
            return;
        }
        try {
            int durationSec = (int) Duration.between(startedAt, Instant.now()).getSeconds();
            boolean escalated = disposition == Disposition.TRANSFERRED;
            metrics.recordCallDuration(durationSec);
            metrics.disposition(disposition);

            long companyId = records.companyIdOf(callAttemptId);
            StoredFile stored = storage.upload(wav, companyId, wav.getFileName().toString());
            records.finishAttempt(callAttemptId, disposition, stored != null ? stored.id() : null, durationSec);
            if (stored != null) {
                storage.deleteLocalCopy(wav);
            }

            String transcript = records.transcriptText(callAttemptId);
            ScenarioDefinition scenario = scenarioService.requireScenario(scenarioId).definition();
            CallSummary summary = summaryService.summarize(transcript, scenario);
            if (summary != null) {
                Long crmNoteId = crmClient.postNote(companyId, clientId, summary);
                records.writeResult(callAttemptId, summary, escalated, crmNoteId);

                // Smart Callback Rescheduling: If the customer asked to be called at a specific time
                if (summary.callbackAt() != null && !summary.callbackAt().isBlank()) {
                    long targetId = records.targetIdOf(callAttemptId);
                    if (targetId != 0L) {
                        Instant callbackInstant = parseCallbackInstant(summary.callbackAt());
                        if (callbackInstant != null && callbackInstant.isAfter(Instant.now())) {
                            campaignService.scheduleCallback(targetId, callbackInstant);
                        }
                    }
                }

                // Hostile Sentiment Alert
                if (summary.sentiment() == Sentiment.HOSTILE) {
                    notificationService.notify(companyId, NotificationType.OPERATOR_REQUEST,
                            "Salbiy muloqot aniqlandi",
                            "Qo'ng'iroqda (ID: " + callAttemptId + ") mijoz keskin norozilik bildirdi: " + summary.summary(),
                            null);
                }

                log.info("Finalized call {} (dur={}s, disposition={}, sentiment={})",
                        callAttemptId, durationSec, disposition, summary.sentiment());
            } else {
                log.info("Finalized call {} without summary (LLM unavailable or empty transcript)", callAttemptId);
            }

            writeTechnicalDetail(callAttemptId, companyId, disposition, channelName, trunk, technical);
        } catch (Exception e) {
            log.warn("Finalization failed for call {}: {}", callAttemptId, e.getMessage());
        }
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
     */
    private void writeTechnicalDetail(long callAttemptId, long companyId, Disposition disposition, String channelName,
                                      String trunk, DialogTechnicalSnapshot technical) {
        String amdResult = amdResult(disposition);
        EffectiveEngineConfig engine = engineConfigService.findEffectiveByCompanyId(companyId);
        String ttsProvider = engine.ttsProvider();
        String ttsVoiceName = null;
        if (technical != null && technical.ttsVoice() != null) {
            TtsVoice voice = voices.find(technical.ttsVoice());
            if (voice != null) {
                ttsProvider = voice.provider();
                ttsVoiceName = voice.name();
            }
        }
        records.writeTechnicalDetail(callAttemptId, channelName, trunk, amdResult,
                engine.sttProvider(), ttsProvider, ttsVoiceName,
                llmModel == null || llmModel.isBlank() ? null : llmModel, technical);
    }

    private String amdResult(Disposition d) {
        if (d == Disposition.VOICEMAIL) {
            return "VOICEMAIL";
        }
        if (vadProps.amd() == null || !vadProps.amd().enabled()) {
            return "DISABLED";
        }
        return "HUMAN";
    }
}

