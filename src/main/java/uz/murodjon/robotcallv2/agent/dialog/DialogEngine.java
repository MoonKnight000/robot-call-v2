package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.aimodel.domain.entity.EffectiveAiModelConfig;
import uz.murodjon.robotcallv2.aimodel.application.service.AiModelConfigService;
import uz.murodjon.robotcallv2.callrecord.application.service.CallRecordService;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.company.application.service.CompanyConfigService;
import uz.murodjon.robotcallv2.company.application.service.CompanyService;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.voice.domain.entity.EffectiveVoiceSettings;
import uz.murodjon.robotcallv2.voice.application.service.VoiceSettingsService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * The cascade pipeline's conversation engine (PROJECT.md §4, §7.1, Stage 7): STT → LLM
 * → TTS, one stage per collaborator.
 */
@Service
public class DialogEngine implements CallDialog {

    private static final Logger log = LoggerFactory.getLogger(DialogEngine.class);

    private final DialogProperties props;
    private final CallRecordService records;
    private final AiModelConfigService aiModelConfigService;
    private final VoiceSettingsService voiceSettingsService;
    private final CompanyService companyService;
    private final CompanyConfigService companyConfigService;

    private final DialogTranscript transcript;
    private final SpeechOutput speech;
    private final TurnRunner turnRunner;
    private final ClientInputGate inputGate;
    private final SilenceWatchdogRunner watchdogRunner;
    private final DialogExecutors executors;

    private final Map<String, DialogSession> sessions = new ConcurrentHashMap<>();

    public DialogEngine(DialogProperties props,
                        CallRecordService records,
                        AiModelConfigService aiModelConfigService,
                        VoiceSettingsService voiceSettingsService,
                        CompanyService companyService,
                        CompanyConfigService companyConfigService,
                        DialogTranscript transcript,
                        SpeechOutput speech,
                        TurnRunner turnRunner,
                        ClientInputGate inputGate,
                        SilenceWatchdogRunner watchdogRunner,
                        DialogExecutors executors) {
        this.props = props;
        this.records = records;
        this.aiModelConfigService = aiModelConfigService;
        this.voiceSettingsService = voiceSettingsService;
        this.companyService = companyService;
        this.companyConfigService = companyConfigService;
        this.transcript = transcript;
        this.speech = speech;
        this.turnRunner = turnRunner;
        this.inputGate = inputGate;
        this.watchdogRunner = watchdogRunner;
        this.executors = executors;
    }

    @Override
    public boolean available() {
        return turnRunner.available();
    }

    public void startCall(String channelId, RtpEndpoint endpoint, CallContext context,
                          ScenarioDefinition scenario, String language, String ttsVoice, boolean disclosureEnabled,
                          Runnable hangup, Runnable transfer, long callAttemptId) {
        startCall(channelId, endpoint, context, scenario, language, ttsVoice, Map.of(), disclosureEnabled,
                hangup, transfer, callAttemptId, true);
    }

    public void startCall(String channelId, RtpEndpoint endpoint, CallContext context,
                          ScenarioDefinition scenario, String language, String ttsVoice,
                          Map<String, String> languageVoices, boolean disclosureEnabled,
                          Runnable hangup, Runnable transfer, long callAttemptId, boolean emotionAdaptiveVoice) {
        if (!available()) {
            log.debug("Dialog not started for {} (engine unavailable)", channelId);
            return;
        }
        long companyId = records.companyIdOf(callAttemptId);
        EffectiveAiModelConfig aiModel = aiModelConfigService.findEffectiveByCompanyId(companyId);
        EffectiveVoiceSettings voiceSettings = voiceSettingsService.effective(companyId);
        Company company = companyService.findById(companyId);
        String companyName = company != null ? company.name() : null;
        CompanyConfig companyConfig = companyConfigService.find(companyId);
        String companyDisclosure = companyConfig != null ? companyConfig.disclosureText() : null;
        DialogSession session = new DialogSession(channelId, language, ttsVoice, context, scenario, endpoint,
                hangup, transfer, callAttemptId, watchdogRunner.createWatchdog(), disclosureEnabled, companyName,
                companyDisclosure, aiModel, voiceSettings, emotionAdaptiveVoice, languageVoices);
        sessions.put(channelId, session);
        log.info("Dialog started [{}] lang={} voice={} state={}",
                channelId, language, ttsVoice != null ? ttsVoice : "default", session.state());
        watchdogRunner.start(session);
        executors.submit(() -> {
            if (!awaitCallerReady(session)) {
                return;
            }
            speakDisclosure(session);
            if (session.isCancelled()) {
                log.info("[{}] caller spoke over the disclosure — greeting deferred to their turn",
                        channelId);
                turnRunner.scheduleFalseInterruptionCheck(session, session.turnCount(),
                        () -> turnRunner.startGreeting(session));
                return;
            }
            turnRunner.startGreeting(session);
        });
    }

    private boolean awaitCallerReady(DialogSession s) {
        if (props.greetingDelayMs() <= 0) {
            return true;
        }
        try {
            Thread.sleep(props.greetingDelayMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return !s.isEnded();
    }

    private void speakDisclosure(DialogSession s) {
        if (!props.mandatoryDisclosure() || !s.disclosureEnabled()) {
            return;
        }
        String line = DialogLines.disclosure(s);
        speech.speakChunk(s, line);
        s.setDisclosureSpoken(true);
        transcript.recordAgentLine(s, line);
        log.info("[{}] disclosure: {}", s.channelId(), line);
    }

    public void onClientFinal(String channelId, String text) {
        onClientFinal(channelId, text, 1f);
    }

    /**
     * @param confidence what the recognizer thought of its own answer, 0..1 — {@code 0}
     *                   from a provider that reports none. Below
     *                   {@code low-confidence-threshold} the turn is told to confirm what
     *                   it heard before acting on it
     */
    public void onClientFinal(String channelId, String text, float confidence) {
        if (text == null || text.isBlank()) {
            return;
        }
        DialogSession session = sessions.get(channelId);
        if (session == null || session.isEnded()) {
            return;
        }
        session.latency().clientFinal();
        session.clientAnswerEnded();
        float floor = props.lowConfidenceThreshold();
        // Zero means the provider does not report confidence at all; treating that as
        // "unsure" would put the confirmation on every single turn.
        session.setLowConfidenceInput(floor > 0 && confidence > 0 && confidence < floor);
        inputGate.onFinal(session, text);
    }

    /**
     * The recognizer has been told the caller finished, after {@code eouWaitMs} of silence.
     * The turn has not started yet — this is the moment the caller stopped talking, which
     * is where the wait they actually experience begins ({@code TurnLatency}).
     */
    public void notifyUtteranceEnd(String channelId, int eouWaitMs) {
        DialogSession session = sessions.get(channelId);
        if (session == null || session.isEnded()) {
            return;
        }
        session.latency().utteranceEnded(eouWaitMs);
    }

    public void onClientInterim(String channelId, String text) {
        DialogSession session = sessions.get(channelId);
        if (session == null) {
            return;
        }
        // An interim is the caller mid-sentence, which is the one thing the silence
        // watchdog could not see: it only knew about bot audio, a turn in flight, and
        // finals. A caller explaining something for longer than no-input-seconds was
        // therefore prompted with "Alo, eshityapsizmi?" over the top of their own
        // sentence — on a real call, 370ms before their final arrived.
        session.touchActivity();
        session.noteInterim();
        if (session.claimBackchannel(props.backchannelAfterMs())) {
            // Off the recognizer's thread: synthesis can take a network round trip, and
            // this one is holding the rest of the caller's audio behind it.
            int turn = session.turnCount();
            executors.submit(() -> speech.speakBackchannel(session, turn));
        }
        if (session.claimInterjection(props.interjectAfterMs())) {
            executors.submit(() -> speech.interject(session));
        }
        turnRunner.speculate(session, text);
    }

    public boolean notifyBargeIn(String channelId) {
        DialogSession session = sessions.get(channelId);
        if (session == null || session.isEnded()) {
            return false;
        }
        return inputGate.onBargeIn(session);
    }

    @Override
    public boolean owns(String channelId) {
        return sessions.containsKey(channelId);
    }

    @Override
    public DialogOutcome outcome(String channelId) {
        DialogSession session = sessions.get(channelId);
        return session != null
                ? new DialogOutcome(session.disposition(), session.doNotCallReason())
                : DialogOutcome.NONE;
    }

    @Override
    public DialogTechnicalSnapshot technicalSnapshot(String channelId) {
        DialogSession session = sessions.get(channelId);
        if (session == null) {
            return DialogTechnicalSnapshot.NONE;
        }
        return new DialogTechnicalSnapshot(
                session.turnCount(),
                session.promptTokens(), session.completionTokens(), session.cachedTokens(),
                session.avgTurnLatencyMs(), session.maxTurnLatencyMs(),
                session.avgLlmLatencyMs(), session.maxLlmLatencyMs(),
                session.ttsVoice(), session.language());
    }

    @Override
    public boolean notifyVoicemail(String channelId) {
        DialogSession session = sessions.get(channelId);
        if (session == null || session.isEnded()) {
            return false;
        }
        session.setCancelled(true);
        session.endpoint().flushPlayback();
        session.end(Disposition.VOICEMAIL);
        log.info("[{}] answering machine detected — ending call as VOICEMAIL", channelId);
        return true;
    }

    @Override
    public void endCall(String channelId) {
        DialogSession session = sessions.remove(channelId);
        if (session == null) {
            return;
        }
        ScheduledFuture<?> task = session.watchdogTask();
        if (task != null) {
            task.cancel(false);
        }
        session.discardSpeculation();
        if (session.factViolations() > 0) {
            log.warn("[{}] fact guard blocked {} sentence(s) this call",
                    channelId, session.factViolations());
        }
    }

    @Override
    public List<LiveDialogSnapshot> liveDialogs() {
        return sessions.values().stream()
                .filter(s -> !s.isEnded())
                .map(s -> new LiveDialogSnapshot(
                        s.channelId(),
                        s.startedAt(),
                        s.state(),
                        s.language(),
                        asString(s.context(), "clientName")))
                .toList();
    }

    @Override
    public OperatorSnapshot operatorSnapshot(String channelId) {
        DialogSession s = sessions.get(channelId);
        if (s == null) {
            return null;
        }
        CallContext c = s.context();
        return new OperatorSnapshot(
                channelId,
                asString(c, "clientName"),
                asString(c, "debtAmount"),
                asString(c, "currency"),
                asString(c, "dueDate"),
                asString(c, "contractNumber"),
                s.state(),
                records.transcriptText(s.callAttemptId()));
    }

    public DialogSession findSession(String channelId) {
        return sessions.get(channelId);
    }

    private static String asString(CallContext c, String factName) {
        if (c == null) {
            return null;
        }
        Object value = c.fact(factName);
        return value == null ? null : value.toString();
    }
}
