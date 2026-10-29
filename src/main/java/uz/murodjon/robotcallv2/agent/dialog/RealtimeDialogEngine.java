package uz.murodjon.robotcallv2.agent.dialog;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.audio.SpeechLevelNormalizer;
import uz.murodjon.robotcallv2.agent.audio.StreamingDownsampler;
import uz.murodjon.robotcallv2.agent.metrics.RealtimeMetrics;
import uz.murodjon.robotcallv2.agent.realtime.*;
import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.agent.session.CallMdc;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AgentWebhookUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.enums.RealtimeFactsDelivery;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentWebhookEvent;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;
import uz.murodjon.robotcallv2.shared.dialog.DialogPhrases;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.voice.application.port.input.TtsVoiceUseCase;
import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Drives a call that runs on a speech-to-speech engine ({@code PipelineMode.REALTIME}) —
 * the counterpart to {@link DialogEngine}, which drives the cascade pipeline.
 *
 * <p>Two engines rather than one with a mode switch, because almost nothing in the middle
 * is shared: there is no utterance to wait for, no prompt to rebuild per turn, no reply to
 * inspect before speaking, no sentence to synthesize. What this class does is narrow —
 * connect the engine, carry audio both ways, run the tools it asks for
 * ({@link RealtimeToolRunner}), and record what the call produced
 * ({@link RealtimeTranscriptRecorder}) — and keeping it separate means the cascade path,
 * which every call runs on today, is untouched by any of it.
 *
 * <p>What it owns besides that is what the platform owes a call whoever does the talking:
 * the same measurements the cascade pipeline takes (turn latency, tokens), the same
 * silence watchdog and limits ({@link RealtimeWatchdogRunner}), and a transcript of what
 * the caller actually heard — a reply the caller cut into is recorded up to where it was
 * cut, not as the engine wrote it.
 *
 * <p>In REALTIME mode, all audio and speech generation (including the opening greeting
 * and §11.1 legal disclosure) are performed natively by the speech-to-speech provider's
 * voice to maintain 100% voice consistency throughout the call.
 *
 * <p>In HALF_CASCADE mode the engine still thinks and speaks in its own voice, but it never
 * hears the caller: our STT does, and each finished utterance is handed to the engine as
 * text ({@link #onCallerFinal}). The engine's own recognizer wrote Uzbek callers down as
 * Russian or Turkish, and answered what it misheard.
 */
@Component
public class RealtimeDialogEngine implements CallDialog {

    private static final Logger log = LoggerFactory.getLogger(RealtimeDialogEngine.class);

    /** The phone leg runs at 8 kHz: this many samples make a millisecond of playback. */
    private static final int SAMPLES_PER_MS = 8;

    /**
     * The synthetic caller line that gets the engine to speak first. Never heard by
     * anyone: it is a nudge, not a greeting, and what the caller hears is the answer.
     */
    private static final String GREETING_BOOTSTRAP =
            "[SYSTEM: the call has started, the client has picked up. Start the conversation.]";
    private static final String TOKEN_CAP = "[SYSTEM: the budget allotted to this conversation is used up. Say a "
            + "polite goodbye in one sentence and end the conversation.]";
    private static final String LOW_CONFIDENCE = "[SYSTEM: the client's last sentence was heard unclearly. If it "
            + "contained a number, date or name — repeat it back and have them confirm it.]";
    /** The languages a reply is sorted into for the {@code voice.realtime.language} metric. */
    private static final List<String> LANGUAGE_TAGS = List.of("uz", "ru");

    /**
     * Sent once, after the opening, when the §11.1 line cannot be found in what the engine
     * said — typically because the caller spoke over the greeting. The requirement is that
     * the caller heard it, not that it was in the instructions.
     */
    private static final String DISCLOSURE_NUDGE = "[SYSTEM: the legal disclosure was not spoken in full. "
            + "Say it now in one sentence: \"%s\" — and continue the conversation from where it stopped.]";

    /**
     * A supervisor's guidance ({@link LiveCallControlHub}), worded as the cascade prompt
     * words it. Added to the conversation as context, never answered on its own.
     */
    private static final String OPERATOR_WHISPER = "[SYSTEM: OPERATOR INSTRUCTION — the client does not hear this; "
            + "do not read it out word for word, just act on it for the rest of the conversation: %s]";

    /** Sent when the call reaches its turn cap, the realtime counterpart of the cascade farewell. */
    private static final String TURN_CAP = "[SYSTEM: the conversation has gone on too long. Say a polite "
            + "goodbye in one sentence and call endCall.]";

    private final RealtimeProviderRegistry registry;
    private final RealtimeSystemPromptFactory promptFactory;
    private final CallAttemptUseCase callAttemptUseCase;
    private final CompanyUseCase companyUseCase;
    private final CompanyConfigUseCase companyConfigService;
    private final DialogProperties dialogProperties;
    private final DialogTuningProperties dialogTuningProperties;
    private final RealtimeProperties realtimeProperties;
    private final RealtimeMetrics realtimeMetrics;
    private final TtsVoiceUseCase ttsVoiceUseCase;
    private final RealtimeWatchdogRunner watchdogRunner;
    private final LanguageDetector languageDetector;
    private final MidCallSmsSender midCallSmsSender;
    private final DialogPolicy dialogPolicy;
    private final RealtimeToolRunner toolRunner;
    private final RealtimeTranscriptRecorder transcriptRecorder;
    private final AgentHandoffResolver agentHandoffResolver;
    private final AgentWebhookUseCase agentWebhookUseCase;
    private final IntentDetector intentDetector;

    private final Map<String, RealtimeDialogSession> sessions = new ConcurrentHashMap<>();
    /**
     * The engine's own delayed and blocking work — the delayed opening request, the drain
     * before a hangup, the cascade fallback — kept off the provider's network thread.
     */
    private final ExecutorService engineTaskExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public RealtimeDialogEngine(RealtimeProviderRegistry registry,
                                RealtimeSystemPromptFactory promptFactory, CallAttemptUseCase callAttemptUseCase,
                                CompanyUseCase companyUseCase, CompanyConfigUseCase companyConfigService,
                                DialogProperties dialogProperties, DialogTuningProperties dialogTuningProperties,
                                RealtimeProperties realtimeProperties,
                                RealtimeMetrics realtimeMetrics,
                                TtsVoiceUseCase ttsVoiceUseCase,
                                RealtimeWatchdogRunner watchdogRunner, LanguageDetector languageDetector,
                                MidCallSmsSender midCallSmsSender, DialogPolicy dialogPolicy,
                                RealtimeToolRunner toolRunner, RealtimeTranscriptRecorder transcriptRecorder,
                                AgentHandoffResolver agentHandoffResolver,
                                AgentWebhookUseCase agentWebhookUseCase,
                                IntentDetector intentDetector) {
        this.registry = registry;
        this.promptFactory = promptFactory;
        this.callAttemptUseCase = callAttemptUseCase;
        this.companyUseCase = companyUseCase;
        this.companyConfigService = companyConfigService;
        this.dialogProperties = dialogProperties;
        this.dialogTuningProperties = dialogTuningProperties;
        this.realtimeProperties = realtimeProperties;
        this.realtimeMetrics = realtimeMetrics;
        this.ttsVoiceUseCase = ttsVoiceUseCase;
        this.watchdogRunner = watchdogRunner;
        this.languageDetector = languageDetector;
        this.midCallSmsSender = midCallSmsSender;
        this.dialogPolicy = dialogPolicy;
        this.toolRunner = toolRunner;
        this.transcriptRecorder = transcriptRecorder;
        this.agentHandoffResolver = agentHandoffResolver;
        this.agentWebhookUseCase = agentWebhookUseCase;
        this.intentDetector = intentDetector;
    }

    @Override
    public boolean available() {
        return !registry.isEmpty();
    }

    @Override
    public boolean owns(String channelId) {
        return sessions.containsKey(channelId);
    }

    /**
     * Connect the engine for {@code start}'s call and let it open the conversation.
     * {@code start.ttsVoice()} is only honoured when it belongs to the engine that is
     * actually running — see {@link #voiceFor}.
     *
     * @param fallback continues the call on the cascade pipeline when the engine drops out
     *                 mid-conversation and cannot be recovered; null hangs up instead
     * @param halfCascade our STT hears the caller and the engine is handed the text; the
     *                 engine thinks and speaks in its own voice (PipelineMode.HALF_CASCADE)
     * @return false when no engine could be opened — nothing has been said to the caller,
     *         so the caller of this method may still run the call some other way
     */
    public boolean startCall(CallStart start, RealtimeAudioBridge bridge, Runnable fallback, boolean halfCascade) {
        String channelId = start.channelId();
        RtpEndpoint endpoint = start.endpoint();
        CallContext context = start.context();
        ScenarioDefinition scenario = start.scenario();
        String language = start.language();
        String voice = start.ttsVoice();
        AiAgent agent = start.agent();
        Runnable hangup = start.hangup();
        Runnable transfer = start.transfer();
        long callAttemptId = start.callAttemptId();
        Consumer<String> dtmf = start.dtmf();
        long companyId = callAttemptUseCase.findCompanyIdByAttemptId(callAttemptId);
        callAttemptUseCase.recordAgent(callAttemptId, agent);
        CompanyConfig companyConfig = companyConfigService.find(companyId);
        AgentPersona agentPersona = DialogPolicy.resolvePersona(agent, companyConfig);
        boolean disclosureEnabled = agentPersona == AgentPersona.AI_ASSISTANT;
        String preferredRealtime = (agent != null && agent.speechEngine().realtimeProvider() != null && !agent.speechEngine().realtimeProvider().isBlank())
                ? agent.speechEngine().realtimeProvider().trim()
                : null;
        RealtimeProvider provider = registry.findForCall(preferredRealtime);
        if (provider == null) {
            log.error("[{}] REALTIME was selected but no engine resolved — call cannot start", channelId);
            return false;
        }
        RealtimeDialogSession session = new RealtimeDialogSession(channelId, language, callAttemptId,
                companyId, agent, scenario, context, endpoint, hangup, transfer);
        session.setWebhookEventPublisher((event, data) -> agentWebhookUseCase.executeEvent(
                companyId, callAttemptId, channelId, agent, event, data));
        session.setSttDriven(halfCascade);
        session.setDtmfSender(dtmf);
        session.setInbound(callAttemptId != 0 && callAttemptUseCase.findInboundRouteIdByAttemptId(callAttemptId) != null);
        if (agent != null && (agent.callBehaviour().midCallSmsEnabled() || StageEntry.sendsSms(scenario))) {
            session.setMidCallSmsSender(text -> midCallSmsSender.send(companyId, callAttemptId, channelId, context, text));
        }
        Company company = companyUseCase.findById(companyId);
        String companyName = company != null ? company.name() : null;

        String disclosureText = null;
        if (dialogProperties.mandatoryDisclosure()) {
            // A HUMAN_LIKE agent was allowed not to say it is a machine, not to leave out
            // that the call is recorded: it opens with the recording notice alone.
            disclosureText = disclosureEnabled
                    ? DialogPolicy.resolveDisclosureLine(session.scenario(),
                            companyConfig != null ? companyConfig.disclosureText() : null,
                            session.language(), companyName)
                    : DialogPhrases.recordingNotice(language);
        }
        if (disclosureText != null) {
            Map<String, Object> facts = context != null && context.facts() != null ? context.facts() : Map.of();
            session.setPendingDisclosure(PromptTemplateEngine.render(disclosureText.trim(), facts));
        }

        boolean factsInPrompt = factsInPrompt(agent);
        String prompt = promptFactory.build(session, companyName, disclosureText, voice, agentPersona, factsInPrompt);
        session.setTools(toolRunner.buildTools(session, factsInPrompt));
        String spokenDisclosure = disclosureText;
        session.setAgentHandoff(agentId -> handOff(session, companyId, agentId,
                next -> promptFactory.build(session, companyName, spokenDisclosure, voice, agentPersona, factsInPrompt),
                factsInPrompt));
        if (provider.lacksToolCalling() && !session.tools().isEmpty()) {
            // Saving an agent on such an engine is refused, but an older agent or the
            // configured default can still land here. Nothing has been said yet, so the
            // cascade pipeline takes the call rather than a bot that cannot hang up.
            log.error("[{}] realtime engine {} cannot call tools ({} needed) — call goes to the cascade pipeline",
                    channelId, provider.name(), session.tools().size());
            realtimeMetrics.realtimeFallback(provider.name());
            return false;
        }
        RealtimeCallConfig config = new RealtimeCallConfig(channelId, language, prompt,
                voiceFor(voice, provider), session.tools(),
                agent != null ? agent.speechEngine().pipecatStt() : null,
                agent != null ? agent.speechEngine().pipecatLlm() : null,
                agent != null ? agent.speechEngine().pipecatTts() : null,
                agent != null ? agent.llmModel() : null);
        // The catalog id only counts as the call's voice if the engine took it (voiceFor).
        session.setEngineIdentity(provider.name(), provider.resolveModel(config),
                config.voice() != null ? voice : null);
        RealtimeSession engine = null;
        try {
            EngineListener listener = new EngineListener(session, provider.outputSampleRate(), fallback, bridge);
            engine = provider.startSession(config, listener);
            bridge.arm(engine, provider.inputSampleRate(), endpoint::recentOutboundLevel,
                    realtimeProperties.echoGateRatio(), realtimeProperties.echoMinBotLevel());
            session.setEngine(engine);
            session.setAudioBridge(bridge);
            sessions.put(channelId, session);
            if (!listener.goLive()) {
                // The engine reported ready and then closed before the session was even
                // registered. That is a failed start: registering it anyway left a dead
                // realtime session next to the cascade fallback its close had started, and
                // the call's outcome went to the dead one.
                log.error("[{}] realtime engine {} closed during setup", channelId, provider.name());
                sessions.remove(channelId, session);
                engine.close();
                realtimeMetrics.realtimeFallback(provider.name());
                return false;
            }
            requestOpening(session, engine);
            watchdogRunner.start(session, this::finishWhenSpoken);
            log.info("Realtime dialog started [{}] engine={} lang={} state={} facts={}",
                    channelId, provider.name(), language, session.state(), factsInPrompt ? "prompt" : "tool");
            return true;
        } catch (Exception e) {
            log.error("[{}] realtime engine {} failed to start: {}", channelId, provider.name(), e.getMessage());
            sessions.remove(channelId);
            if (engine != null) {
                engine.close();
            }
            realtimeMetrics.realtimeFallback(provider.name());
            return false;
        }
    }

    /**
     * An AGENT_TRANSFER stage on a realtime call: switch the session to the target agent's
     * flow, then hand the engine the prompt and tools rebuilt for it. An engine that only
     * takes its configuration at setup refuses, and the session goes back to the flow it
     * was in, so the stage takes its own fallback transition.
     */
    private boolean handOff(RealtimeDialogSession session, long companyId, long agentId,
                            Function<ScenarioDefinition, String> promptFor, boolean factsInPrompt) {
        RealtimeSession engine = session.engine();
        ScenarioDefinition next = agentHandoffResolver.findPublishedScenario(companyId, agentId);
        if (engine == null || next == null || next.stages() == null || next.stages().isEmpty()) {
            return false;
        }
        ScenarioDefinition previous = session.scenario();
        String previousState = session.state();
        session.switchScenario(next);
        List<ToolCallback> tools = toolRunner.buildTools(session, factsInPrompt);
        if (!engine.updateConfiguration(promptFor.apply(next), tools)) {
            session.switchScenario(previous);
            session.setState(previousState);
            log.info("[{}] realtime engine {} cannot change its script mid-call — agent handoff skipped",
                    session.channelId(), session.engineName());
            return false;
        }
        session.setTools(tools);
        return true;
    }

    /**
     * Asks the engine for its opening line after the same quiet moment the cascade
     * pipeline leaves ({@code greeting-delay-ms}): a greeting on the very first frame
     * lands on the click of the handset and the caller's own "alo".
     */
    private void requestOpening(RealtimeDialogSession session, RealtimeSession engine) {
        int delayMs = dialogProperties.greetingDelayMs();
        if (delayMs <= 0) {
            session.markOpeningRequested();
            engine.sendUserText(GREETING_BOOTSTRAP);
            return;
        }
        engineTaskExecutor.execute(() -> CallMdc.with(session.channelId(), () -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (session.isEnded()) {
                return;
            }
            session.markOpeningRequested();
            engine.sendUserText(GREETING_BOOTSTRAP);
        }));
    }

    /**
     * The caller typed on the keypad. A speech-to-speech engine hears audio only, so the digits
     * are told to it in words — the same IVR-style menu a cascade agent gets as a turn.
     */
    public void onDtmf(String channelId, String digit) {
        RealtimeDialogSession s = sessions.get(channelId);
        RealtimeSession engine = s != null ? s.engine() : null;
        if (s == null || s.isEnded() || engine == null) {
            return;
        }
        s.touch();
        engine.sendUserText("[SYSTEM: the client typed on the phone keypad: " + digit
                + ". Take it as a menu choice or as the answer to your question.]");
    }

    /**
     * Give the engine a supervisor's guidance for the rest of this call. It goes in as
     * context ({@link RealtimeSession#sendContext}), so the engine acts on it in its next
     * answer instead of answering the whisper. While a reply is being generated it is held
     * until that reply is done ({@link EngineListener#onTurnComplete}): new content cuts a
     * speaking engine off mid-sentence.
     *
     * @return {@code false} when this engine has no live conversation on {@code channelId}
     */
    public boolean offerWhisper(String channelId, String instruction) {
        RealtimeDialogSession s = sessions.get(channelId);
        RealtimeSession engine = s != null ? s.engine() : null;
        if (s == null || s.isEnded() || engine == null) {
            return false;
        }
        if (s.replyOpen()) {
            s.holdWhisper(instruction);
            log.info("[{}] operator whisper held until the reply ends ({} chars)", channelId, instruction.length());
        } else {
            engine.sendContext(OPERATOR_WHISPER.formatted(instruction));
            log.info("[{}] operator whisper injected ({} chars)", channelId, instruction.length());
        }
        return true;
    }

    /**
     * Whether this call's facts go into the engine's instructions: the agent's own choice
     * where it made one, the deployment's otherwise.
     */
    private boolean factsInPrompt(AiAgent agent) {
        RealtimeFactsDelivery delivery = agent != null ? agent.dataPolicy().realtimeFactsDelivery() : null;
        return delivery == null ? realtimeProperties.factsInPrompt() : delivery == RealtimeFactsDelivery.PROMPT;
    }

    /**
     * The caller finished an utterance — told by our own VAD, the one clock every engine
     * is timed against. Only counts when the line was free: an "aha" over the bot's own
     * sentence is agreement, not a turn handed over.
     *
     * @param waitMs the silence the VAD already waited out before saying so
     */
    public void notifyUtteranceEnd(String channelId, int waitMs) {
        RealtimeDialogSession s = sessions.get(channelId);
        if (s == null || s.isEnded() || s.endpoint().isPlaying()) {
            return;
        }
        s.markCallerStopped(System.nanoTime() - waitMs * 1_000_000L);
        s.touch();
    }

    /** Whether a REALTIME call's transcript lines come from our STT ({@link #onCallerFinal}). */
    public boolean usesTranscriptStt() {
        return realtimeProperties.transcriptStt();
    }

    /**
     * One finished caller utterance from our own STT. It always goes into the transcript.
     * On HALF_CASCADE it is also the caller's turn: the engine never got the audio, so this
     * text is what it answers. On REALTIME ({@code transcript-stt}) the engine has already
     * heard the audio and answers that, so the text is only written down.
     */
    public void onCallerFinal(String channelId, String text) {
        RealtimeDialogSession s = sessions.get(channelId);
        if (s == null || s.isEnded() || text == null || text.isBlank()) {
            return;
        }
        String said = text.trim();
        s.touch();
        if (s.isSttDriven() && isEchoOfBot(s, said)) {
            // No echo cancellation on the line: handed to the engine, the bot's own words
            // would be answered as if the caller had said them.
            CallMdc.with(channelId, () -> log.warn("[{}] dropped a caller transcript that echoes the bot's own line: {}",
                    channelId, said));
            return;
        }
        CallMdc.with(channelId, () -> {
            transcriptRecorder.recordLine(s, "CLIENT", said);
            log.debug("[{}] FINAL (stt): {}", channelId, said);
        });
        if (s.isSttDriven() && s.endpoint().isPlaying() && FloorRules.isBackchannel(said)) {
            CallMdc.with(channelId, () -> log.debug("[{}] caller backchannel ignored while bot is playing: '{}'", channelId, said));
            return;
        }
        detectIntentIfSelected(s, said);
        RealtimeSession engine = s.engine();
        if (s.isSttDriven() && engine != null) {
            engine.sendUserText(said);
        }
    }

    /** The same test the cascade pipeline applies ({@code ClientInputGate}). */
    private boolean isEchoOfBot(RealtimeDialogSession s, String text) {
        String caller = TranscriptText.normalize(text);
        return caller.length() >= dialogTuningProperties.minEchoChars()
                && TranscriptText.normalize(s.lastAgentText()).contains(caller);
    }

    /**
     * HALF_CASCADE: the caller's utterance so far, while it is still being spoken. The engine
     * does not hear the line, so taking the floor from it is decided here: more than a word
     * or two over the bot's playback cuts the bot off — a lone "aha" does not.
     */
    public void onCallerInterim(String channelId, String text) {
        RealtimeDialogSession s = sessions.get(channelId);
        if (s == null || !s.isSttDriven() || s.isEnded() || text == null) {
            return;
        }
        s.touch();
        RtpEndpoint endpoint = s.endpoint();
        int needed = Math.max(1, dialogProperties.minInterruptionWords());
        if (!endpoint.isPlaying() || Backchannels.words(text).length < needed) {
            return;
        }
        if (FloorRules.isBackchannel(text)) {
            CallMdc.with(channelId, () -> log.debug("[{}] caller backchannel in realtime interim ignored: '{}'", channelId, text));
            return;
        }
        // Only a reply still being produced has more audio coming. One already complete is
        // merely still playing: marking it would drop the answer to the caller's next turn.
        s.setDiscardingReply(s.replyOpen());
        cutPlayback(s);
        CallMdc.with(channelId, () -> log.debug("[{}] caller took the floor", channelId));
    }

    /**
     * Stop what the caller is hearing and note how much of the reply reached them, so the
     * transcript (and an engine that accepts it, {@link RealtimeSession#truncatePlayback})
     * keeps only the heard part.
     */
    private void cutPlayback(RealtimeDialogSession s) {
        RtpEndpoint endpoint = s.endpoint();
        long start = s.replyStartSample();
        if (start >= 0) {
            long queued = endpoint.queuedSamples() - start;
            long played = Math.max(0, endpoint.playedSamples() - start);
            if (queued > 0 && played < queued) {
                s.setHeardShare(played / (double) queued);
                realtimeMetrics.realtimeInterruption(s.engineName());
                s.recordBargeIn();
                RealtimeSession engine = s.engine();
                if (engine != null) {
                    engine.truncatePlayback(played / SAMPLES_PER_MS);
                }
            }
        }
        s.endReply();
        endpoint.flushPlayback();
        s.touch();
    }

    /**
     * The caller is talking. The engine does its own barge-in, so this interrupts nothing
     * — it only keeps the silence watchdog quiet and restarts the turnaround clock.
     *
     * @return always false: nothing was interrupted here, so the VAD stays armed
     */
    public boolean notifyCallerSpeaking(String channelId) {
        RealtimeDialogSession s = sessions.get(channelId);
        if (s != null) {
            s.clearCallerStopped();
            s.touch();
        }
        return false;
    }

    /**
     * The provider-side voice name for a campaign's catalog voice id, or null to leave the
     * engine on its configured default.
     *
     * <p>Voice names do not cross vendors: a realtime engine's are its own
     * ({@code Aoede}, {@code Puck}) and mean nothing to a TTS vendor, and the reverse is
     * equally true. A campaign that was set up while the company ran on the cascade
     * pipeline still holds a Yandex voice id, so the owning provider is checked before the
     * name is forwarded — sending {@code nigora} to Gemini Live would fail the whole setup
     * over a cosmetic setting.
     */
    private String voiceFor(String voiceId, RealtimeProvider provider) {
        if (voiceId == null || voiceId.isBlank()) {
            return null;
        }
        TtsVoice voice = ttsVoiceUseCase.find(voiceId);
        if (voice == null) {
            return null;
        }
        if (!provider.name().equals(voice.provider())) {
            log.debug("Voice {} belongs to {}, not the running engine {} — using the engine default",
                    voiceId, voice.provider(), provider.name());
            return null;
        }
        return voice.name();
    }

    @Override
    public DialogOutcome outcome(String channelId) {
        RealtimeDialogSession s = sessions.get(channelId);
        return s == null ? DialogOutcome.NONE
                : new DialogOutcome(GoalOutcome.settle(s.disposition(), s.scenario(), s.outcome()), s.doNotCallReason());
    }

    /**
     * Tokens and turn latency as the engine reported and our VAD measured them. There is
     * no separate LLM latency on a realtime call — recognition, reasoning and speech are
     * one wait — so those two stay null, and synthesis is never billed by the character.
     */
    @Override
    public DialogTechnicalSnapshot technicalSnapshot(String channelId) {
        RealtimeDialogSession s = sessions.get(channelId);
        if (s == null) {
            return DialogTechnicalSnapshot.NONE;
        }
        // No recognizer confidence on a realtime call: the engine hears the audio itself.
        return new DialogTechnicalSnapshot(s.turnCount(),
                s.promptTokens(), s.completionTokens(), s.cachedTokens(), 0,
                s.avgTurnLatencyMs(), s.maxTurnLatencyMs(), null, null,
                s.ttsVoice(), s.language(), s.engineName(), s.llmModel(),
                s.bargeIns(), s.watchdog() != null ? s.watchdog().prompts() : 0, 0);
    }

    @Override
    public boolean notifyVoicemail(String channelId) {
        RealtimeDialogSession s = sessions.get(channelId);
        if (s == null || s.isEnded()) {
            return false;
        }
        s.end(Disposition.VOICEMAIL);
        return true;
    }

    @Override
    public void endCall(String channelId) {
        RealtimeDialogSession s = sessions.remove(channelId);
        if (s == null) {
            return;
        }
        RealtimeSession engine = s.engine();
        if (engine != null) {
            engine.close();
        }
        release(s);
        log.info("Realtime dialog ended [{}] turns={} disposition={}",
                channelId, s.turnCount(), s.disposition());
        if (s.factViolations() > 0) {
            log.error("[{}] {} fact-guard violation(s) were spoken this call", channelId, s.factViolations());
        }
    }

    /** What every way out of a realtime call has to do once: stop the watchdog, count the session. */
    private void release(RealtimeDialogSession s) {
        watchdogRunner.stop(s);
        realtimeMetrics.realtimeSessionSeconds(s.engineName(), Duration.between(s.startedAt(), Instant.now()).getSeconds());
        RealtimeAudioBridge bridge = s.audioBridge();
        if (bridge != null) {
            realtimeMetrics.realtimeEchoSuppressed(bridge.echoSuppressedSeconds());
            // A cascade fallback replaces the line's listeners without closing them, and
            // the recognizer stream behind the bridge would stay open for the rest of the call.
            try {
                bridge.close();
            } catch (IOException e) {
                log.warn("[{}] could not close the caller's recognizer: {}", s.channelId(), e.getMessage());
            }
        }
    }

    @Override
    public List<LiveDialogSnapshot> liveDialogs() {
        return sessions.values().stream()
                .filter(s -> !s.isEnded())
                .map(s -> new LiveDialogSnapshot(s.channelId(), s.startedAt(), s.state(), s.language(),
                        factText(s.context(), "clientName")))
                .toList();
    }

    @Override
    public OperatorSnapshot operatorSnapshot(String channelId) {
        RealtimeDialogSession s = sessions.get(channelId);
        if (s == null) {
            return null;
        }
        // A call with no facts (a manual test line) has no context at all, as on the cascade pipeline.
        CallContext c = s.context();
        return new OperatorSnapshot(channelId,
                factText(c, "clientName"), factText(c, "debtAmount"), factText(c, "currency"),
                factText(c, "dueDate"), factText(c, "contractNumber"),
                s.state(), s.transferReason(), s.transcriptText(),
                StageEntry.findTransferTarget(s.scenario(), s.state()));
    }

    private static String factText(CallContext c, String factName) {
        if (c == null) {
            return null;
        }
        Object value = c.fact(factName);
        return value == null ? null : value.toString();
    }

    /**
     * The words of {@code text} a caller who heard {@code share} of its audio can have
     * heard — cut at a word boundary, and null when that is nothing at all. Speech rate
     * is close enough to even over one reply that the share of audio is the share of words;
     * the cascade pipeline, which knows each sentence's own audio, measures per sentence.
     */
    static String heardPart(String text, double share) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] words = text.trim().split("\\s+");
        int keep = (int) Math.round(words.length * Math.max(0, Math.min(1, share)));
        if (keep <= 0) {
            return null;
        }
        return keep >= words.length ? text.trim() : String.join(" ", Arrays.copyOfRange(words, 0, keep));
    }

    /**
     * After the opening turn: if the §11.1 line does not appear in what the caller heard,
     * ask the engine to say it now. Once per call, whatever the check finds.
     */
    private void checkDisclosure(RealtimeDialogSession s) {
        String disclosure = s.pendingDisclosure();
        if (disclosure == null || s.spokenTurnCount() < 1) {
            return;
        }
        s.clearPendingDisclosure();
        if (DisclosureCheck.covered(disclosure, s.openingSpeech())) {
            return;
        }
        log.warn("[{}] the disclosure was not heard in the opening — asking the engine to say it", s.channelId());
        RealtimeSession engine = s.engine();
        if (engine != null) {
            engine.sendUserText(DISCLOSURE_NUDGE.formatted(disclosure));
        }
    }

    /** Past the turn cap, the engine is asked to close — the same cap the cascade pipeline has. */
    private void enforceTurnCap(RealtimeDialogSession s) {
        int maxTurns = dialogProperties.maxTurns();
        if (maxTurns <= 0 || s.isEnded() || s.spokenTurnCount() < maxTurns) {
            return;
        }
        log.warn("[{}] realtime call reached {} spoken turns — closing", s.channelId(), maxTurns);
        watchdogRunner.requestFarewell(s, TURN_CAP, this::finishWhenSpoken);
    }

    /** Carries one engine's output into the call: its audio, its words, its tool calls. */
    private final class EngineListener implements RealtimeListener {

        private final RealtimeDialogSession session;
        /**
         * Carries the anti-aliasing filter across the engine's chunks: resampling each one
         * on its own put a click at every chunk boundary (StreamingDownsampler). Null when
         * the engine already speaks at the telephone rate.
         */
        private final StreamingDownsampler downsampler;
        /** Null when output level normalization is switched off. */
        private final SpeechLevelNormalizer normalizer;
        /** Runs the call on the cascade pipeline instead; null leaves hanging up as the only option. */
        private final Runnable fallback;
        /**
         * The caller's side of the line. Armed as soon as the session is open, but only
         * opened once the engine has started speaking — {@link RealtimeAudioBridge#open()}
         * says why.
         */
        private final RealtimeAudioBridge bridge;

        private static final int STARTING = 0;
        private static final int LIVE = 1;
        private static final int CLOSED_DURING_SETUP = 2;
        /** Whether startCall has registered the session yet, or the engine closed before it did. */
        private final AtomicInteger phase = new AtomicInteger(STARTING);

        private EngineListener(RealtimeDialogSession session, int engineRate, Runnable fallback,
                               RealtimeAudioBridge bridge) {
            this.session = session;
            this.downsampler = switch (engineRate) {
                case 8000 -> null;
                case 24000 -> StreamingDownsampler.from24kTo8k();
                default -> StreamingDownsampler.from16kTo8k();
            };
            this.normalizer = realtimeProperties.normalizeOutputLevel() ? new SpeechLevelNormalizer() : null;
            this.fallback = fallback;
            this.bridge = bridge;
        }

        @Override
        public void onBotAudio(short[] pcm) {
            bridge.open();
            if (session.isDiscardingReply()) {
                // HALF_CASCADE: the caller cut this reply off; the rest of it is not played.
                session.touch();
                return;
            }
            short[] telephone = downsampler == null ? pcm : downsampler.push(pcm, pcm.length);
            if (telephone.length == 0) {
                return;
            }
            if (normalizer != null) {
                normalizer.process(telephone, telephone.length);
            }
            RtpEndpoint endpoint = session.endpoint();
            if (!session.replyOpen()) {
                beginReply(endpoint.queuedSamples());
            }
            endpoint.enqueuePcm(telephone);
            session.touch();
        }

        /** The first audio of a reply: where truncation measures from, and where the caller's wait ends. */
        private void beginReply(long startSample) {
            long now = System.nanoTime();
            session.startReply(startSample);
            Duration opening = session.takeOpeningLatency(now);
            if (opening != null) {
                realtimeMetrics.recordRealtimeFirstAudio(session.engineName(), opening);
            }
            Long turnaround = session.takeTurnaroundMs(now);
            if (turnaround != null) {
                session.recordTurnLatency(turnaround);
                realtimeMetrics.recordRealtimeTurnaround(session.engineName(), Duration.ofMillis(turnaround));
            }
        }

        @Override
        public void onInputTranscript(String text, boolean isFinal) {
            session.touch();
            // With our own recognizer on this call, the caller's lines come from onCallerFinal.
            if (isFinal && !bridge.hasTranscriptTap()) {
                detectIntentIfSelected(session, text);
                CallMdc.with(session.channelId(), () -> {
                    transcriptRecorder.recordLine(session, "CLIENT", text);
                    // What the caller said is personal data: kept in the transcript, not in INFO logs.
                    log.debug("[{}] FINAL: {}", session.channelId(), text);
                });
            }
        }

        @Override
        public void onOutputTranscript(String text) {
            CallMdc.with(session.channelId(), () -> {
                Double share = session.takeHeardShare();
                String heard = share == null ? text : heardPart(text, share);
                if (heard == null) {
                    log.debug("[{}] AGENT (cut off before a word was heard): {}", session.channelId(), text);
                    return;
                }
                transcriptRecorder.recordLine(session, "AGENT", heard);
                session.setLastAgentText(heard);
                if (share == null) {
                    log.debug("[{}] AGENT: {}", session.channelId(), heard);
                } else {
                    log.debug("[{}] AGENT (cut off at {}%): {}", session.channelId(), Math.round(share * 100), heard);
                }
                // What was never played was never said: only the heard part is audited.
                transcriptRecorder.auditFacts(session, heard);
                session.appendOpeningSpeech(heard);
                String spokenLanguage = languageDetector.detect(heard, LANGUAGE_TAGS);
                if (spokenLanguage != null) {
                    realtimeMetrics.realtimeLanguage(session.engineName(), spokenLanguage);
                }
            });
        }

        @Override
        public void onInterrupted() {
            // The engine dropped the reply the caller had cut off; what follows is new.
            session.setDiscardingReply(false);
            cutPlayback(session);
            CallMdc.with(session.channelId(), () -> log.debug("[{}] caller barged in", session.channelId()));
        }

        @Override
        public void onTurnComplete() {
            // Also here, not only on audio: a first turn that only called a tool would
            // otherwise leave the caller unheard for the rest of the call.
            bridge.open();
            session.countTurn();
            // A reply the caller cut off is over; the next one is played again.
            session.setDiscardingReply(false);
            boolean spoke = session.replyOpen();
            session.closeReply();
            // A whisper that came in while this reply was being generated goes in now.
            String whisper = session.takeHeldWhisper();
            RealtimeSession engine = session.engine();
            if (whisper != null && engine != null && !session.isEnded()) {
                engine.sendContext(OPERATOR_WHISPER.formatted(whisper));
            }
            // A cut-off reply whose transcript never came must not trim the next one.
            session.takeHeardShare();
            if (spoke) {
                session.countSpokenTurn();
            }
            session.touch();
            CallMdc.with(session.channelId(), () -> {
                if (session.isEnded()) {
                    engineTaskExecutor.execute(() -> CallMdc.with(session.channelId(), () -> finishWhenSpoken(session)));
                    return;
                }
                checkDisclosure(session);
                enforceTurnCap(session);
            });
        }

        /** See {@link RealtimeToolRunner#handleToolCall}. */
        @Override
        public void onToolCall(String callId, String name, String argumentsJson) {
            toolRunner.handleToolCall(session, callId, name, argumentsJson);
        }

        @Override
        public void onUsage(long promptTokens, long completionTokens, long cachedTokens) {
            session.addUsage(promptTokens, completionTokens, cachedTokens);
            realtimeMetrics.recordRealtimeUsage(session.engineName(), promptTokens, completionTokens, cachedTokens);
            // Cost cap (§C14), the same budget the cascade pipeline closes on.
            long cap = session.maxTokensPerCall();
            if (DialogPolicy.isTokenBudgetSpent(cap, session.tokensUsed()) && !session.isEnded()) {
                CallMdc.with(session.channelId(), () -> {
                    log.warn("[{}] realtime call spent {} tokens of {} — closing", session.channelId(),
                            session.tokensUsed(), cap);
                    watchdogRunner.requestFarewell(session, TOKEN_CAP, RealtimeDialogEngine.this::finishWhenSpoken);
                });
            }
        }

        @Override
        public void onLowConfidenceInput(String text) {
            RealtimeSession engine = session.engine();
            if (engine == null || session.isEnded()) {
                return;
            }
            CallMdc.with(session.channelId(), () -> log.debug("[{}] low-confidence transcript, asking to confirm: {}",
                    session.channelId(), text));
            engine.sendUserText(LOW_CONFIDENCE);
        }

        @Override
        public void onReconnected() {
            realtimeMetrics.realtimeReconnect(session.engineName());
        }

        @Override
        public void onEngineWorking(boolean working) {
            session.setEngineWorking(working);
        }

        /** Marks the session registered; false when the engine already closed before that. */
        boolean goLive() {
            return phase.compareAndSet(STARTING, LIVE);
        }

        @Override
        public void onClosed(Throwable cause) {
            String reason = cause != null ? cause.getMessage() : "closed by the engine";
            if (phase.compareAndSet(STARTING, CLOSED_DURING_SETUP)) {
                // startCall is still setting the call up; it sees this and reports a failed
                // start, so the caller's fallback runs once, from there.
                CallMdc.with(session.channelId(), () -> log.warn("[{}] realtime engine closed during setup: {}",
                        session.channelId(), reason));
                return;
            }
            // Only a session still registered for this call is this listener's to act on. One
            // already removed was closed by this side — a hangup, a teardown — and its close is
            // expected. A clean close used to be ignored outright, which left a caller whose
            // engine the server had closed normally (code 1000) mid-call in dead air.
            if (!sessions.remove(session.channelId(), session)) {
                return;
            }
            CallMdc.with(session.channelId(), () -> {
                log.warn("[{}] realtime engine closed: {}", session.channelId(), reason);
                release(session);
                // A call that was already on its way out only needs the line dropped once
                // the goodbye has played; starting it over on another pipeline would greet
                // a caller who was just told goodbye.
                if (session.isEnded()) {
                    engineTaskExecutor.execute(() -> CallMdc.with(session.channelId(), () -> finishWhenSpoken(session)));
                    return;
                }
                // Reached only once the provider has exhausted its own recovery — Gemini
                // Live, for one, resumes onto a fresh connection first and only reports here
                // when even that failed. The caller is still on the line either way, so the
                // cascade pipeline takes over rather than the call being dropped on them.
                realtimeMetrics.realtimeFallback(session.engineName());
                Runnable continuation = fallback != null ? fallback : session.hangup();
                engineTaskExecutor.execute(() -> CallMdc.with(session.channelId(), continuation));
            });
        }
    }

    private void detectIntentIfSelected(RealtimeDialogSession session, String text) {
        if (!session.isWebhookEventEnabled(AgentWebhookEvent.INTENT_DETECTED)
                || text == null || text.isBlank() || FloorRules.isBackchannel(text)) {
            return;
        }
        intentDetector.detectAsync(text, null, detected -> session.publishWebhookEvent(
                AgentWebhookEvent.INTENT_DETECTED,
                Map.of("intent", detected.intent().name(), "confidence", detected.confidence())));
    }

    /**
     * Hang up — or transfer — once whatever is still queued has played. Reached from the
     * engine's turn completing, from the watchdog's grace timer and from a lost engine on
     * a call that was already ending, so it runs at most once per call.
     */
    private void finishWhenSpoken(RealtimeDialogSession s) {
        if (!s.beginClosing()) {
            return;
        }
        if (!dialogPolicy.awaitPlaybackDrained(s.endpoint())) {
            return;
        }
        Runnable action = DialogPolicy.selectClosingAction(s.disposition(), s.transfer(), s.hangup());
        if (action != null) {
            action.run();
        }
    }

    @PreDestroy
    public void shutdown() {
        engineTaskExecutor.shutdownNow();
    }
}
