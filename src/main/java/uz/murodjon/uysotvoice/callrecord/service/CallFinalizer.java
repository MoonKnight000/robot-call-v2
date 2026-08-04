package uz.murodjon.uysotvoice.callrecord.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.agent.dialog.CallSummary;
import uz.murodjon.uysotvoice.agent.dialog.DialogTechnicalSnapshot;
import uz.murodjon.uysotvoice.agent.metrics.VoiceMetrics;
import uz.murodjon.uysotvoice.agent.stt.SttProperties;
import uz.murodjon.uysotvoice.agent.summary.SummaryService;
import uz.murodjon.uysotvoice.agent.vad.VadProperties;
import uz.murodjon.uysotvoice.crm.service.CrmClient;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioDefinition;
import uz.murodjon.uysotvoice.scenario.service.ScenarioService;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;
import uz.murodjon.uysotvoice.storage.dto.StoredFile;
import uz.murodjon.uysotvoice.storage.service.AudioStorageService;
import uz.murodjon.uysotvoice.voice.dto.TtsVoice;
import uz.murodjon.uysotvoice.voice.service.TtsVoiceService;

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
    private final ScenarioService scenarioService;
    private final VoiceMetrics metrics;
    private final SttProperties sttProps;
    private final TtsVoiceService voices;
    private final VadProperties vadProps;
    private final String llmModel;

    public CallFinalizer(CallRecordService records, SummaryService summaryService,
                         AudioStorageService storage, CrmClient crmClient, ScenarioService scenarioService,
                         VoiceMetrics metrics, SttProperties sttProps, TtsVoiceService voices,
                         VadProperties vadProps,
                         @Value("${spring.ai.google.genai.chat.options.model:}") String llmModel) {
        this.records = records;
        this.summaryService = summaryService;
        this.storage = storage;
        this.crmClient = crmClient;
        this.scenarioService = scenarioService;
        this.metrics = metrics;
        this.sttProps = sttProps;
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
            // With storage off (or the upload failing), the recording just stays on local
            // disk with no stored_file row — retrievable by an operator with shell access,
            // but not through GET /api/files/{id}. Accepted tradeoff of running with
            // storage off; a recording is evidence in a dispute (§11.3), so we never
            // discard the only copy just because it isn't catalogued.
            records.finishAttempt(callAttemptId, disposition, stored != null ? stored.id() : null, durationSec);
            // Only once the upload is confirmed: deleting on a failed upload would
            // destroy the only copy of a recording that may be needed as evidence (§11.3).
            if (stored != null) {
                storage.deleteLocalCopy(wav);
            }

            String transcript = records.transcriptText(callAttemptId);
            ScenarioDefinition scenario = scenarioService.requireScenario(scenarioId).definition();
            CallSummary summary = summaryService.summarize(transcript, scenario);
            if (summary != null) {
                Long crmNoteId = crmClient.postNote(companyId, clientId, summary);
                records.writeResult(callAttemptId, summary, escalated, crmNoteId);
                log.info("Finalized call {} (dur={}s, disposition={}, sentiment={})",
                        callAttemptId, durationSec, disposition, summary.sentiment());
            } else {
                log.info("Finalized call {} without summary (LLM unavailable or empty transcript)", callAttemptId);
            }

            writeTechnicalDetail(callAttemptId, disposition, channelName, trunk, technical);
        } catch (Exception e) {
            log.warn("Finalization failed for call {}: {}", callAttemptId, e.getMessage());
        }
    }

    /**
     * Resolves the bits of the "Texnik" tab (§10.5) that do not need to be captured
     * live — STT/TTS provider, LLM model, AMD result — and persists everything
     * together with what {@link DialogTechnicalSnapshot} already accumulated.
     */
    private void writeTechnicalDetail(long callAttemptId, Disposition disposition, String channelName,
                                      String trunk, DialogTechnicalSnapshot technical) {
        String amdResult = amdResult(disposition);
        String ttsProvider = null;
        String ttsVoiceName = null;
        if (technical != null && technical.ttsVoice() != null) {
            TtsVoice voice = voices.find(technical.ttsVoice());
            if (voice != null) {
                ttsProvider = voice.provider();
                ttsVoiceName = voice.name();
            }
        }
        records.writeTechnicalDetail(callAttemptId, channelName, trunk, amdResult,
                sttProps.provider(), ttsProvider, ttsVoiceName,
                llmModel == null || llmModel.isBlank() ? null : llmModel, technical);
    }

    /**
     * AMD ran without persisting a result of its own (see {@code AnsweringMachineDetector}
     * — it only fires a "detected" callback); both outcomes are reconstructed here instead:
     * a voicemail disposition means it fired, otherwise it either cleared the call as human
     * or never ran at all.
     */
    private String amdResult(Disposition disposition) {
        if (disposition == Disposition.VOICEMAIL) {
            return "MACHINE";
        }
        return vadProps.amd() != null && vadProps.amd().enabled() ? "HUMAN" : null;
    }
}
