package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.ai.chat.client.ChatClient;
import uz.murodjon.robotcallv2.agent.provider.OwnLlmChatModels;
import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.agent.stt.SttProperties;
import uz.murodjon.robotcallv2.agent.tts.TtsProperties;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelConfigUseCase;
import uz.murodjon.robotcallv2.aimodel.domain.entity.EffectiveAiModelConfig;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallTranscriptUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;
import uz.murodjon.robotcallv2.shared.dialog.DialogPhrases;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.tool.application.port.input.ToolUseCase;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.voice.application.port.input.VoiceSettingsUseCase;
import uz.murodjon.robotcallv2.voice.domain.entity.EffectiveVoiceSettings;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

/**
 * The cascade pipeline's conversation engine (PROJECT.md §4, §7.1, Stage 7): STT → LLM
 * → TTS, one stage per collaborator.
 */
@Service
public class DialogEngine implements CallDialog {

    private static final Logger log = LoggerFactory.getLogger(DialogEngine.class);

    private final DialogProperties dialogProperties;
    private final CallAttemptUseCase callAttemptUseCase;
    private final CallTranscriptUseCase callTranscriptUseCase;
    private final AiModelConfigUseCase aiModelConfigUseCase;
    private final VoiceSettingsUseCase voiceSettingsUseCase;
    private final CompanyUseCase companyUseCase;
    private final CompanyConfigUseCase companyConfigService;

    private final DialogTranscript transcript;
    private final SpeechOutput speech;
    private final TurnRunner turnRunner;
    private final ClientInputGate inputGate;
    private final SilenceWatchdogRunner watchdogRunner;
    private final DialogExecutors executors;
    private final ToolUseCase toolUseCase;
    private final MidCallSmsSender midCallSmsSender;
    private final CallBillingUseCase callBillingUseCase;
    private final VoicemailDrop voicemailDrop;
    private final SttProperties sttProperties;
    private final TtsProperties ttsProperties;
    private final OwnLlmChatModels ownLlmChatModels;

    private final Map<String, DialogSession> sessions = new ConcurrentHashMap<>();

    public DialogEngine(DialogProperties dialogProperties,
                        CallAttemptUseCase callAttemptUseCase,
                        CallTranscriptUseCase callTranscriptUseCase,
                        AiModelConfigUseCase aiModelConfigUseCase,
                        VoiceSettingsUseCase voiceSettingsUseCase,
                        CompanyUseCase companyUseCase,
                        CompanyConfigUseCase companyConfigService,
                        DialogTranscript transcript,
                        SpeechOutput speech,
                        TurnRunner turnRunner,
                        ClientInputGate inputGate,
                        SilenceWatchdogRunner watchdogRunner,
                        DialogExecutors executors,
                        ToolUseCase toolUseCase,
                        MidCallSmsSender midCallSmsSender,
                        CallBillingUseCase callBillingUseCase,
                        VoicemailDrop voicemailDrop,
                        SttProperties sttProperties,
                        TtsProperties ttsProperties,
                        OwnLlmChatModels ownLlmChatModels) {
        this.dialogProperties = dialogProperties;
        this.callAttemptUseCase = callAttemptUseCase;
        this.callTranscriptUseCase = callTranscriptUseCase;
        this.aiModelConfigUseCase = aiModelConfigUseCase;
        this.voiceSettingsUseCase = voiceSettingsUseCase;
        this.companyUseCase = companyUseCase;
        this.companyConfigService = companyConfigService;
        this.transcript = transcript;
        this.speech = speech;
        this.turnRunner = turnRunner;
        this.inputGate = inputGate;
        this.watchdogRunner = watchdogRunner;
        this.executors = executors;
        this.toolUseCase = toolUseCase;
        this.midCallSmsSender = midCallSmsSender;
        this.callBillingUseCase = callBillingUseCase;
        this.voicemailDrop = voicemailDrop;
        this.sttProperties = sttProperties;
        this.ttsProperties = ttsProperties;
        this.ownLlmChatModels = ownLlmChatModels;
    }

    @Override
    public boolean available() {
        return turnRunner.available();
    }

    /** Starts the cascade conversation on {@code start}'s call; does nothing when the engine is unavailable. */
    public void startCall(CallStart start) {
        begin(start, false);
    }

    /**
     * The same conversation for a simulated caller ({@code VoiceCallSimulator}): the real
     * audio pipeline, but none of the company's REST or MCP tools and no mid-call SMS — a
     * test persona must never place a real order or text a real number.
     */
    public void startSimulatedCall(CallStart start) {
        begin(start, true);
    }

    private void begin(CallStart start, boolean simulated) {
        String channelId = start.channelId();
        RtpEndpoint endpoint = start.endpoint();
        CallContext context = start.context();
        ScenarioDefinition scenario = start.scenario();
        String language = start.language();
        String ttsVoice = start.ttsVoice();
        AiAgent agent = start.agent();
        Runnable hangup = start.hangup();
        Runnable transfer = start.transfer();
        long callAttemptId = start.callAttemptId();
        Consumer<String> dtmf = start.dtmf();
        if (!available()) {
            log.debug("Dialog not started for {} (engine unavailable)", channelId);
            return;
        }
        long companyId = callAttemptUseCase.findCompanyIdByAttemptId(callAttemptId);
        callAttemptUseCase.recordAgent(callAttemptId, agent);
        // The agent's own model settings sit over the company's: one company runs a cheap
        // model for a survey and a careful one for collections, on the same day.
        EffectiveAiModelConfig configured = aiModelConfigUseCase.findEffectiveByCompanyId(companyId)
                .withOverrides(agent != null ? agent.llmModel() : null,
                        agent != null ? agent.temperature() : null,
                        agent != null ? agent.maxOutputTokens() : null);
        // With the balance enforced the call ends before it costs more than the company has.
        EffectiveAiModelConfig aiModel = configured.withMaxCallSeconds(CallLimits.capByBalance(
                configured.maxCallSeconds(), callBillingUseCase.findAffordableCallSeconds(companyId, false)));
        // A company on its own LLM talks to nothing else: a key that cannot be read fails the
        // call here rather than sending the conversation to the platform's model instead.
        ChatClient ownChatClient = aiModel.ownLlm() != null
                ? TurnTools.chatClient(ownLlmChatModels.createChatModel(companyId, aiModel.ownLlm()))
                : null;
        // The agent picks the vendor that speaks it, the same way it picks its model;
        // the company setting is what an agent that chose none falls back to. A call
        // whose voice comes from the catalog still speaks through that voice's own
        // provider — only a call with no voice of its own reaches this choice.
        EffectiveVoiceSettings voiceSettings = voiceSettingsUseCase.findEffectiveByCompanyId(companyId);
        String agentTtsProvider = agent != null ? agent.speechEngine().ttsProvider() : null;
        if (agentTtsProvider != null && !agentTtsProvider.isBlank()) {
            voiceSettings = voiceSettings.withProvider(agentTtsProvider.trim());
        }
        Company company = companyUseCase.findById(companyId);
        String companyName = company != null ? company.name() : null;
        CompanyConfig companyConfig = companyConfigService.find(companyId);
        String companyDisclosure = companyConfig != null ? companyConfig.disclosureText() : null;
        AgentPersona agentPersona = DialogPolicy.resolvePersona(agent, companyConfig);
        boolean disclosureEnabled = agentPersona == AgentPersona.AI_ASSISTANT;
        boolean emotionAdaptiveVoice = agent == null || agent.voice().emotionAdaptive();
        Map<String, String> languageVoices = agent != null ? agent.voice().perLanguage() : Map.of();
        List<Tool> tools = List.of();
        if (!simulated && agent != null && agent.id() != 0L) {
            try {
                tools = toolUseCase.findAgentToolDefinitions(companyId, agent.id());
            } catch (Exception e) {
                log.warn("[{}] failed to load active tools for agent {}: {}", channelId, agent.id(), e.getMessage());
            }
        }
        String firstMessage = agent != null ? agent.script().firstMessage() : null;
        DialogSession session = DialogSession.builder(channelId, language)
                .ttsVoice(ttsVoice)
                .context(context)
                .scenario(scenario)
                .endpoint(endpoint)
                .hangup(hangup)
                .transfer(transfer)
                .callAttemptId(callAttemptId)
                .companyId(companyId)
                .watchdog(watchdogRunner.createWatchdog())
                .disclosureEnabled(disclosureEnabled)
                .companyName(companyName)
                .companyDisclosureText(companyDisclosure)
                .aiModel(aiModel)
                .voiceSettings(voiceSettings)
                .emotionAdaptiveVoice(emotionAdaptiveVoice)
                .agentPersona(agentPersona)
                .languageVoices(languageVoices)
                .firstMessage(firstMessage)
                .tools(tools)
                .pronunciationRules(agent != null ? agent.pronunciationRules() : List.of())
                .agent(agent)
                .build();
        if (simulated) {
            session.setMcpTools(List.of());
        } else if (agent != null && agent.callBehaviour().midCallSmsEnabled()) {
            session.setMidCallSmsSender(smsText ->
                    midCallSmsSender.send(companyId, callAttemptId, channelId, session.context(), smsText));
        }
        session.setDtmfSender(dtmf);
        boolean inbound = callAttemptId != 0 && callAttemptUseCase.findInboundRouteIdByAttemptId(callAttemptId) != null;
        session.setInbound(inbound);
        session.setDetectLanguages(companyConfigService.findSupportedLanguageCodes(companyId));
        // Which model a one-word turn is answered on is the agent's call, the same way its
        // own llmModel is: a survey agent and a collections agent share a deployment and
        // not a tolerance for a weaker model. Null leaves the installation default.
        session.setFastModel(agent != null ? agent.fastLlmModel() : null);
        session.setOwnChatClient(ownChatClient);
        String agentSttProvider = agent != null ? agent.speechEngine().sttProvider() : null;
        session.latency().providers(
                agentSttProvider != null && !agentSttProvider.isBlank() ? agentSttProvider.trim() : sttProperties.provider(),
                voiceSettings.provider() != null ? voiceSettings.provider() : ttsProperties.provider());
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
        if (dialogProperties.greetingDelayMs() <= 0) {
            return true;
        }
        try {
            Thread.sleep(dialogProperties.greetingDelayMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return !s.isEnded();
    }

    private void speakDisclosure(DialogSession s) {
        if (!dialogProperties.mandatoryDisclosure()) {
            return;
        }
        if (s.agentPersona() == AgentPersona.HUMAN_LIKE) {
            // The company was allowed an agent that does not say it is a machine; the call
            // is recorded all the same, and that the caller is told. Not marked as the
            // disclosure: the agent still greets and introduces itself on its first turn.
            String notice = DialogPhrases.recordingNotice(s.language());
            speech.speakChunk(s, notice);
            transcript.recordAgentLine(s, notice);
            log.info("[{}] recording notice: {}", s.channelId(), notice);
            return;
        }
        if (!s.disclosureEnabled()) {
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
        float floor = dialogProperties.lowConfidenceThreshold();
        // Zero means the provider does not report confidence at all; treating that as
        // "unsure" would put the confirmation on every single turn.
        boolean lowConfidence = floor > 0 && confidence > 0 && confidence < floor;
        session.setLowConfidenceInput(lowConfidence);
        if (lowConfidence) {
            session.recordLowConfidenceInput();
        }
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
        inputGate.onInterim(session, text);
        if (session.claimBackchannel(dialogProperties.backchannelAfterMs())) {
            // Off the recognizer's thread: synthesis can take a network round trip, and
            // this one is holding the rest of the caller's audio behind it.
            int turn = session.turnCount();
            executors.submit(() -> speech.speakBackchannel(session, turn));
        }
        if (session.claimInterjection(dialogProperties.interjectAfterMs())) {
            executors.submit(() -> speech.interject(session));
        }
        turnRunner.speculate(session, text);
    }

    /** A key on the caller's keypad — see {@link ClientInputGate#onKeypress}. */
    public void notifyKeypress(String channelId) {
        DialogSession session = sessions.get(channelId);
        if (session == null || session.isEnded()) {
            return;
        }
        inputGate.onKeypress(session);
    }

    /** The keys typed since the first {@link #notifyKeypress}, as one caller turn. */
    public void onKeypadInput(String channelId, String digits) {
        DialogSession session = sessions.get(channelId);
        if (session != null) {
            session.setTyping(false);
        }
        onClientFinal(channelId, digits);
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
                ? new DialogOutcome(GoalOutcome.settle(session.disposition(), session.scenario(), session.outcome()),
                        session.doNotCallReason())
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
                session.ttsChars(),
                session.avgTurnLatencyMs(), session.maxTurnLatencyMs(),
                session.avgLlmLatencyMs(), session.maxLlmLatencyMs(),
                session.ttsVoice(), session.language(),
                null, llmModelOf(session),
                session.bargeIns(), session.watchdog() != null ? session.watchdog().prompts() : 0,
                session.lowConfidenceInputs(),
                session.ownChatClient() != null);
    }

    /** The model the call ran on — the company's own endpoint's when it has one. */
    private static String llmModelOf(DialogSession session) {
        EffectiveAiModelConfig aiModel = session.aiModel();
        if (aiModel == null) {
            return null;
        }
        return aiModel.ownLlm() != null ? aiModel.ownLlm().model() : aiModel.model();
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

    /**
     * The audio heuristic found a voicemail on a call whose agent leaves a message: leave it
     * after the beep, then hang up ({@link VoicemailDrop}).
     *
     * @return false when there is no live dialog to leave it from — the caller hangs up instead
     */
    public boolean leaveVoicemail(String channelId, String message) {
        DialogSession session = sessions.get(channelId);
        if (session == null || session.isEnded()) {
            return false;
        }
        session.setDisposition(Disposition.VOICEMAIL);
        return voicemailDrop.start(session, message);
    }

    @Override
    public void endCall(String channelId) {
        DialogSession session = sessions.remove(channelId);
        if (session == null) {
            return;
        }
        // Stop the turn in flight, not only the ones after it. Without this a hung-up call
        // still read its LLM stream to the end (and paid for it), synthesized the sentences,
        // ran the turn's tools — webhooks, SMS — and wrote transcript lines after the
        // finalizer had already summarized the call.
        session.markLineGone();
        session.setCancelled(true);
        session.end(null);
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
                s.transferReason(),
                callTranscriptUseCase.findTranscriptText(s.callAttemptId()));
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
