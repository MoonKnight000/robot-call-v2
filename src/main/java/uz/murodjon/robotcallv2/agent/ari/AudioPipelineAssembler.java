package uz.murodjon.robotcallv2.agent.ari;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.audio.*;
import uz.murodjon.robotcallv2.agent.dialog.*;
import uz.murodjon.robotcallv2.agent.metrics.CallMetrics;
import uz.murodjon.robotcallv2.agent.metrics.SttMetrics;
import uz.murodjon.robotcallv2.agent.realtime.RealtimeAudioBridge;
import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.agent.session.CallMdc;
import uz.murodjon.robotcallv2.agent.session.CallSession;
import uz.murodjon.robotcallv2.agent.stt.*;
import uz.murodjon.robotcallv2.agent.turn.*;
import uz.murodjon.robotcallv2.agent.vad.AmdProperties;
import uz.murodjon.robotcallv2.agent.vad.SileroVad;
import uz.murodjon.robotcallv2.agent.vad.VadProperties;
import uz.murodjon.robotcallv2.agent.vad.VadStream;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.enums.VoicemailAction;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallTranscriptUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.live.application.port.input.LiveUseCase;
import uz.murodjon.robotcallv2.live.infrastructure.config.LiveProperties;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;

/**
 * The listener chain a call's inbound audio runs through: live audio level, the VAD (with
 * the speech gate, Smart Turn and answering-machine detection hanging off it), and either
 * the speech recognizer of the cascade pipeline or a realtime engine's bridge.
 */
@Component
class AudioPipelineAssembler {

    private static final Logger log = LoggerFactory.getLogger(AudioPipelineAssembler.class);

    private final VadProperties vadProperties;
    private final ObjectProvider<SileroVad> vadProvider;
    private final SttProperties sttProperties;
    private final SttProviderSelector sttProviderSelector;
    private final SmartTurnProperties smartTurnProperties;
    private final ObjectProvider<SmartTurnDetector> turnDetectorProvider;
    private final TextTurnProperties textTurnProperties;
    private final ObjectProvider<TextTurnDetector> textTurnDetectorProvider;
    private final LiveProperties liveProperties;
    private final LiveUseCase liveUseCase;
    private final DialogProperties dialogProperties;
    private final DialogEngine dialogEngine;
    private final RealtimeDialogEngine realtimeDialogEngine;
    private final DialogRouter dialogRouter;
    /** The company's own languages, for the recogniser's detect list. */
    private final CompanyConfigUseCase companyConfigService;
    private final CallTranscriptUseCase callTranscriptUseCase;
    private final SttMetrics sttMetrics;
    private final CallMetrics callMetrics;
    private final AriEventLanes ariEventLanes;
    private final CallSessionRegistry callSessionRegistry;
    private final AriClient ariClient;
    private final ExecutorService callExecutor;
    private final TurnDatasetRecorder turnDatasetRecorder;
    private final ObjectProvider<NeuralDenoiser> neuralDenoiserProvider;
    private final RealtimeVoicemailDrop realtimeVoicemailDrop;

    AudioPipelineAssembler(VadProperties vadProperties,
                           ObjectProvider<SileroVad> vadProvider,
                           SttProperties sttProperties,
                           SttProviderSelector sttProviderSelector,
                           SmartTurnProperties smartTurnProperties,
                           ObjectProvider<SmartTurnDetector> turnDetectorProvider,
                           TextTurnProperties textTurnProperties,
                           ObjectProvider<TextTurnDetector> textTurnDetectorProvider,
                           LiveProperties liveProperties,
                           LiveUseCase liveUseCase,
                           DialogProperties dialogProperties,
                           DialogEngine dialogEngine,
                           RealtimeDialogEngine realtimeDialogEngine,
                           DialogRouter dialogRouter,
                           CompanyConfigUseCase companyConfigService,
                           CallTranscriptUseCase callTranscriptUseCase,
                           SttMetrics sttMetrics,
                           CallMetrics callMetrics,
                           AriEventLanes ariEventLanes,
                           CallSessionRegistry callSessionRegistry,
                           AriClient ariClient,
                           ExecutorService callExecutor,
                           TurnDatasetRecorder turnDatasetRecorder,
                           ObjectProvider<NeuralDenoiser> neuralDenoiserProvider,
                           RealtimeVoicemailDrop realtimeVoicemailDrop) {
        this.vadProperties = vadProperties;
        this.vadProvider = vadProvider;
        this.sttProperties = sttProperties;
        this.sttProviderSelector = sttProviderSelector;
        this.smartTurnProperties = smartTurnProperties;
        this.turnDetectorProvider = turnDetectorProvider;
        this.textTurnProperties = textTurnProperties;
        this.textTurnDetectorProvider = textTurnDetectorProvider;
        this.liveProperties = liveProperties;
        this.liveUseCase = liveUseCase;
        this.dialogProperties = dialogProperties;
        this.dialogEngine = dialogEngine;
        this.realtimeDialogEngine = realtimeDialogEngine;
        this.dialogRouter = dialogRouter;
        this.companyConfigService = companyConfigService;
        this.callTranscriptUseCase = callTranscriptUseCase;
        this.sttMetrics = sttMetrics;
        this.callMetrics = callMetrics;
        this.ariEventLanes = ariEventLanes;
        this.callSessionRegistry = callSessionRegistry;
        this.ariClient = ariClient;
        this.callExecutor = callExecutor;
        this.turnDatasetRecorder = turnDatasetRecorder;
        this.neuralDenoiserProvider = neuralDenoiserProvider;
        this.realtimeVoicemailDrop = realtimeVoicemailDrop;
    }

    /**
     * Say out loud, once, when a turn-taking setting is switched on that cannot reach a
     * call.
     *
     * <p>Every one of them works through {@link SpeechGate}, and the gate is only built
     * when {@code stt.vad-gating.enabled} is on — so with the recognizer endpointing for
     * itself (the default) they are configured, reported as enabled, and inert. That is
     * worth a line at startup: the alternative is a tuning session spent moving numbers
     * nothing reads.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warnAboutInertTurnSettings() {
        boolean gated = vadProperties.enabled() && sttProperties.enabled()
                && sttProperties.vadGating() != null && sttProperties.vadGating().enabled();
        if (gated) {
            return;
        }
        if (smartTurnProperties.enabled()) {
            log.warn("voice-agent.turn.enabled is on but no speech gate is installed — Smart Turn "
                    + "(extend and early close) will not run. It needs voice-agent.stt.vad-gating.enabled=true.");
        }
        if (textTurnProperties.enabled()) {
            log.warn("voice-agent.text-turn.enabled is on but no speech gate is installed — the text "
                    + "turn detector will not run. It needs voice-agent.stt.vad-gating.enabled=true.");
        }
        EndpointingProperties endpointing = sttProperties.endpointing();
        if (endpointing != null && endpointing.enabled()) {
            log.warn("voice-agent.stt.endpointing.enabled is on but no speech gate is installed — "
                    + "the recognizer keeps deciding end-of-utterance. It needs voice-agent.stt.vad-gating.enabled=true.");
        }
        if (endpointing != null && endpointing.dynamic() != null && endpointing.dynamic().enabled()) {
            log.warn("voice-agent.stt.endpointing.dynamic.enabled is on but no speech gate is installed — "
                    + "the wait will not adapt. It needs voice-agent.stt.vad-gating.enabled=true.");
        }
    }

    /**
     * Generates the ambient beds before any call needs one. Without this the class
     * initializes on the first frame that mixes a bed, which runs on the RTP pacer's
     * Netty event loop (CLAUDE.md §8.3) and stalls the first call's audio for ~0.6s.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpAmbientSounds() {
        long startNanos = System.nanoTime();
        AmbientSoundGenerator.warmUp();
        log.info("Ambient soundscapes generated in {}ms", (System.nanoTime() - startNanos) / 1_000_000);
    }

    /**
     * Gives the call its own neural denoiser state when the deployment has the model
     * (VOICE-PIPELINE-PLAN Bosqich 6); without it the endpoint keeps the broadband gate.
     */
    void attachDenoiser(RtpEndpoint endpoint) {
        NeuralDenoiser denoiser = neuralDenoiserProvider.getIfAvailable();
        if (denoiser != null && denoiser.available()) {
            endpoint.setNeuralDenoiser(denoiser.newStream());
        }
    }

    /**
     * The call's listeners, in a list the caller may still add to. A non-null
     * {@code realtimeBridge} builds the chain of a realtime call, which has no speech
     * recognizer of its own.
     */
    List<AudioListener> buildAudioListeners(String channelId, long companyId, long callAttemptId,
                                            Instant startedAt, String language,
                                            RealtimeAudioBridge realtimeBridge, CallContext context,
                                            AiAgent agent, DoubleSupplier botLevelSupplier) {
        List<AudioListener> listeners = new ArrayList<>();
        boolean realtime = realtimeBridge != null;

        if (liveProperties.audioLevelEnabled()) {
            listeners.add(new AudioLevelListener(channelId, companyId, liveUseCase));
        }

        SpeechGate speechGate = null;
        if (vadProperties.enabled()) {
            SileroVad vad = vadProvider.getIfAvailable();
            if (vad != null && vad.available()) {
                VadGatingProperties gating = sttProperties.vadGating();
                if (!realtime && gating != null && gating.enabled() && sttProperties.enabled()) {
                    EndpointingProperties endpointing = sttProperties.endpointing();
                    boolean adaptive = endpointing != null && endpointing.enabled();
                    DynamicEndpointingProperties dynamic = adaptive ? endpointing.dynamic() : null;
                    boolean learning = dynamic != null && dynamic.enabled();
                    speechGate = new SpeechGate(CallMediaFactory.SAMPLE_RATE, gating.preRollMs(), gating.postRollMs(),
                            gating.minSpeechMs(),
                            adaptive ? endpointing.shortUtteranceMs() : 0,
                            adaptive ? endpointing.shortSilenceMs() : 0,
                            learning ? dynamic.minPostRollMs() : 0,
                            learning ? dynamic.emaAlpha() : 0d,
                            learning ? dynamic.reopenGraceMs() : 0);
                    speechGate.setTranscriptCues(adaptive ? endpointing.completeSilenceMs() : 0);
                    speechGate.setCloseListener((text, waitMs, continued) -> {
                        sttMetrics.gateCloseJudged(continued);
                        turnDatasetRecorder.record(language, text, waitMs, continued);
                    });
                    if (!installTurnDetector(listeners, speechGate, language)) {
                        installTextTurnDetector(speechGate, language);
                    }
                }
                // Who knows the caller stopped talking. With a gate installed it is the
                // gate's close, and SttStreamBridge reports that. Without one — the
                // default, where the recognizer endpoints for itself — nothing did, and
                // the silence in front of the final sat outside every latency number the
                // app reports (VOICE-QUALITY-PLAN 0.1). The VAD is already scoring every
                // window for barge-in, so it can say so at no extra cost.
                // On a realtime call the engine endpoints and barges in for itself, so the
                // VAD only reports: the engine's turnaround is then timed on the same clock
                // as the cascade pipeline's, and its silence watchdog knows the caller is there.
                IntConsumer onSpeechEnd = realtime
                        ? waitMs -> realtimeDialogEngine.notifyUtteranceEnd(channelId, waitMs)
                        : speechGate != null ? null
                        : waitMs -> dialogEngine.notifyUtteranceEnd(channelId, waitMs);
                listeners.add(new VadStream(vad, vadProperties, channelId,
                        realtime ? () -> realtimeDialogEngine.notifyCallerSpeaking(channelId)
                                : () -> dialogEngine.notifyBargeIn(channelId), speechGate,
                        buildAmd(channelId, realtime), onSpeechEnd, botLevelSupplier));
            } else if (!realtime && sttProperties.vadGating() != null && sttProperties.vadGating().enabled()) {
                log.debug("[{}] STT gating requested but VAD is unavailable — streaming all audio", channelId);
            }
        }

        if (realtime) {
            listeners.add(realtimeBridge);
            return listeners;
        }

        if (sttProperties.enabled()) {
            String preferredSttProvider = (agent != null && agent.speechEngine().sttProvider() != null && !agent.speechEngine().sttProvider().isBlank())
                    ? agent.speechEngine().sttProvider().trim()
                    : sttProperties.provider();
            SttProvider stt = sttProviderSelector.findForCall(preferredSttProvider);
            // A model name only means anything to the vendor it was chosen for, so it is
            // dropped the moment the call is not on that vendor — the requested provider left
            // the build, or the bridge fails over mid-call to whatever else is configured.
            String agentSttModel = (agent != null && sttProviderSelector.exists(preferredSttProvider))
                    ? agent.speechEngine().sttModel()
                    : null;
            if (stt != null) {
                boolean dialog = dialogProperties.enabled() && dialogEngine.available();
                TranscriptListener listener = (text, isFinal, confidence) -> {
                    if (isFinal) {
                        // What the caller said is personal data: DEBUG, like the rest of the pipeline.
                        log.debug("[{}] FINAL (conf={}): {}", channelId, confidence, text);
                        int offsetMs = (int) (Instant.now().toEpochMilli() - startedAt.toEpochMilli());
                        // Off the STT vendor's callback thread, which a slow insert would stall; the
                        // channel's lane keeps the lines in the order they were heard.
                        ariEventLanes.execute(channelId, () -> callTranscriptUseCase.addTranscript(callAttemptId, "CLIENT", text, null,
                                offsetMs, confidence));
                        if (dialog) {
                            dialogEngine.onClientFinal(channelId, text, confidence);
                        }
                    } else {
                        log.debug("[{}] interim: {}", channelId, text);
                        if (dialog) {
                            dialogEngine.onClientInterim(channelId, text);
                        }
                    }
                };
                try {
                    String sttLanguage = (language == null || language.isBlank())
                            ? sttProperties.defaultLanguage() : language;
                    // The company's own languages first; the deployment list only when it set none.
                    List<String> detectLangs = companyConfigService.findSupportedLanguageCodes(companyId);
                    if (detectLangs.isEmpty() && sttProperties.detectLanguages() != null) {
                        detectLangs = sttProperties.detectLanguages();
                    }
                    listeners.add(new SttStreamBridge(stt, stt.sampleRate(), CallMediaFactory.SAMPLE_RATE,
                            channelId, sttLanguage, detectLangs, listener, speechGate, sttMetrics,
                            sttProperties.endpointing(), sttProperties.responseTimeoutMs(),
                            dialog ? eouWaitMs -> dialogEngine.notifyUtteranceEnd(channelId, eouWaitMs) : null,
                            SttHints.of(context, agent != null ? agent.pronunciationRules() : List.of()),
                            sttProviderSelector, agentSttModel, sttProperties.stream()));
                } catch (Exception e) {
                    if (dialog) {
                        // A conversation that cannot hear the caller is dead air until the
                        // reaper; failing setup hangs up now and records why, and the
                        // campaign's retry policy decides whether the number is tried again.
                        throw new ExternalServiceException(ErrorCode.STT_START_FAILED, "stt", e, e.getMessage());
                    }
                    log.warn("STT not started for {}: {}", channelId, e.getMessage());
                }
            }
        }
        return listeners;
    }

    /** @return whether Smart Turn now decides this call's turn ends */
    private boolean installTurnDetector(List<AudioListener> listeners, SpeechGate gate, String language) {
        if (!smartTurnProperties.enabled()) {
            return false;
        }
        SmartTurnDetector detector = turnDetectorProvider.getIfAvailable();
        if (detector == null || !detector.available()) {
            return false;
        }
        String callLanguage = (language == null || language.isBlank())
                ? sttProperties.defaultLanguage() : language;
        if (!smartTurnProperties.supports(callLanguage)) {
            log.debug("Smart Turn skipped for {} — not a language the model was trained for", callLanguage);
            return false;
        }
        UtteranceBuffer buffer = new UtteranceBuffer(detector.samples());
        listeners.add(buffer);
        gate.setTurnDetector(() -> {
            boolean complete = detector.isComplete(buffer.recent(), buffer.length());
            sttMetrics.turnScored(complete);
            return complete;
        }, smartTurnProperties.maxExtendMs());
        if (smartTurnProperties.earlyWaitMs() > 0) {
            gate.setEarlyClose(() -> {
                boolean confident = detector.isConfidentlyComplete(buffer.recent(), buffer.length());
                if (confident) {
                    sttMetrics.turnClosedEarly();
                }
                return confident;
            }, smartTurnProperties.earlyWaitMs());
        }
        return true;
    }

    /**
     * The same two hooks as Smart Turn, judged from the words instead of the audio
     * ({@link TextTurnDetector}): the recognizer's latest interim is what gets scored.
     */
    private void installTextTurnDetector(SpeechGate gate, String language) {
        if (!textTurnProperties.enabled()) {
            return;
        }
        TextTurnDetector detector = textTurnDetectorProvider.getIfAvailable();
        if (detector == null || !detector.available()) {
            return;
        }
        String callLanguage = (language == null || language.isBlank())
                ? sttProperties.defaultLanguage() : language;
        if (!textTurnProperties.supports(callLanguage)) {
            log.debug("Text turn skipped for {} — not a language the model is trusted for", callLanguage);
            return;
        }
        gate.setTurnDetector(() -> {
            boolean complete = detector.isComplete(gate.lastInterimTranscript());
            sttMetrics.turnScored(complete);
            return complete;
        }, textTurnProperties.maxExtendMs());
        if (textTurnProperties.earlyWaitMs() > 0) {
            gate.setEarlyClose(() -> {
                boolean confident = detector.isConfidentlyComplete(gate.lastInterimTranscript());
                if (confident) {
                    sttMetrics.turnClosedEarly();
                }
                return confident;
            }, textTurnProperties.earlyWaitMs());
        }
    }

    private AnsweringMachineDetector buildAmd(String channelId, boolean realtime) {
        AmdProperties amd = vadProperties.amd();
        if (amd == null || !amd.enabled()) {
            return null;
        }
        return new AnsweringMachineDetector(channelId, CallMediaFactory.SAMPLE_RATE, amd.observeMs(),
                amd.minContinuousSpeechMs(), amd.silenceToleranceMs(),
                () -> handleVoicemail(channelId, realtime));
    }

    /** @param realtime whether the call is on a realtime engine, which has no voice of ours to leave a message with */
    private void handleVoicemail(String channelId, boolean realtime) {
        callMetrics.voicemailDetected("audio");
        CallSession session = callSessionRegistry.find(channelId);
        AiAgent agent = session != null ? session.agent() : null;
        // HANGUP only for a call with no agent behind it at all — there is no message to
        // leave. An agent always has an action of its own; the group guarantees it.
        VoicemailAction action = agent != null
                ? agent.callBehaviour().voicemailAction()
                : VoicemailAction.HANGUP;

        if (action == VoicemailAction.IGNORE) {
            log.info("[{}] Voicemail detected, but agent voicemailAction is IGNORE - continuing call", channelId);
            return;
        }

        String message = agent != null ? agent.callBehaviour().voicemailMessage() : null;
        boolean leaveMessage = action.leavesMessage() && message != null && !message.isBlank();
        if (leaveMessage && !realtime && dialogEngine.leaveVoicemail(channelId, message)) {
            // The dialog hangs up once the message has been spoken after the beep.
            return;
        }
        if (leaveMessage && realtime && session != null) {
            // The engine would keep talking over the message, so it goes first; with its
            // session gone, teardown reads the outcome from the registry instead.
            callSessionRegistry.rememberDisposition(channelId, Disposition.VOICEMAIL);
            callExecutor.execute(() -> CallMdc.with(channelId, () -> {
                realtimeDialogEngine.endCall(channelId);
                realtimeVoicemailDrop.start(channelId, session.endpoint(), agent, message);
            }));
            return;
        }

        dialogRouter.notifyVoicemail(channelId);
        callExecutor.execute(() -> CallMdc.with(channelId, () -> ariClient.hangup(channelId)));
    }
}
