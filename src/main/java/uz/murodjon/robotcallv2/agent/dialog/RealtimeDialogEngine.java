package uz.murodjon.robotcallv2.agent.dialog;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.audio.StreamingDownsampler;
import uz.murodjon.robotcallv2.agent.metrics.VoiceMetrics;
import uz.murodjon.robotcallv2.agent.realtime.*;
import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.callrecord.application.service.CallRecordService;
import uz.murodjon.robotcallv2.company.application.service.CompanyConfigService;
import uz.murodjon.robotcallv2.company.application.service.CompanyService;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeRetrievalUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.ToolDef;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;
import uz.murodjon.robotcallv2.shared.dialog.DialogPhrases;
import uz.murodjon.robotcallv2.shared.dialog.Disclosure;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.voice.application.service.TtsVoiceService;
import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Drives a call that runs on a speech-to-speech engine ({@code PipelineMode.REALTIME}) —
 * the counterpart to {@link DialogEngine}, which drives the cascade pipeline.
 *
 * <p>Two engines rather than one with a mode switch, because almost nothing in the middle
 * is shared: there is no utterance to wait for, no prompt to rebuild per turn, no reply to
 * inspect before speaking, no sentence to synthesize. What this class does is narrow —
 * connect the engine, carry audio both ways, run the tools it asks for, and record what
 * the call produced — and keeping it separate means the cascade path, which every call
 * runs on today, is untouched by any of it.
 *
 * <p>In REALTIME mode, all audio and speech generation (including the opening greeting
 * and §11.1 legal disclosure) are performed natively by the speech-to-speech provider's
 * voice to maintain 100% voice consistency throughout the call.
 */
@Component
public class RealtimeDialogEngine implements CallDialog {

    private static final Logger log = LoggerFactory.getLogger(RealtimeDialogEngine.class);

    /** Longest a farewell is allowed to drain before the line is dropped anyway. */
    private static final Duration MAX_DRAIN = Duration.ofSeconds(30);

    /**
     * The synthetic caller line that gets the engine to speak first. Never heard by
     * anyone: it is a nudge, not a greeting, and what the caller hears is the answer.
     */
    private static final String GREETING_BOOTSTRAP =
            "[TIZIM: qo'ng'iroq boshlandi, mijoz go'shakni ko'tardi. Suhbatni boshlang.]";

    private final RealtimeProviderRegistry registry;
    private final RealtimeSystemPromptFactory promptFactory;
    private final CallRecordService records;
    private final CompanyService companyService;
    private final CompanyConfigService companyConfigService;
    private final DialogProperties dialogProperties;
    private final RealtimeProperties realtimeProperties;
    private final VoiceMetrics metrics;
    private final TtsVoiceService voiceCatalog;
    private final KnowledgeRetrievalUseCase knowledgeRetrieval;

    private final Map<String, RealtimeDialogSession> sessions = new ConcurrentHashMap<>();
    /**
     * Tool bodies hit the database and the CRM, and they arrive on the engine's network
     * thread — the same thread its next audio frame comes in on. Virtual threads because
     * the work is all waiting, and there is one per tool call, not one per call.
     */
    private final ExecutorService toolExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public RealtimeDialogEngine(RealtimeProviderRegistry registry,
                                RealtimeSystemPromptFactory promptFactory, CallRecordService records,
                                CompanyService companyService, CompanyConfigService companyConfigService,
                                DialogProperties dialogProperties, RealtimeProperties realtimeProperties,
                                VoiceMetrics metrics, TtsVoiceService voiceCatalog,
                                KnowledgeRetrievalUseCase knowledgeRetrieval) {
        this.registry = registry;
        this.promptFactory = promptFactory;
        this.records = records;
        this.companyService = companyService;
        this.companyConfigService = companyConfigService;
        this.dialogProperties = dialogProperties;
        this.realtimeProperties = realtimeProperties;
        this.metrics = metrics;
        this.voiceCatalog = voiceCatalog;
        this.knowledgeRetrieval = knowledgeRetrieval;
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
     * Connect the engine for {@code channelId} and let it open the conversation.
     *
     * @param voice    the agent's voice for this call's language, resolved the same way
     *                 the cascade pipeline resolves it. Only honoured when it belongs to
     *                 the engine that is actually running — see {@link #voiceFor}
     * @param agent    who is speaking — persona and this agent's own model, over the
     *                 company's. Null on a manual test call, which has no agent behind it
     * @param fallback continues the call on the cascade pipeline when the engine drops out
     *                 mid-conversation and cannot be recovered; null hangs up instead
     * @param dtmf     how this call presses keypad tones for IVR navigation, or null when
     *                 it cannot
     */
    public boolean startCall(String channelId, RtpEndpoint endpoint, CallContext context,
                             ScenarioDefinition scenario, String language, String voice,
                             AiAgent agent,
                             Runnable hangup, Runnable transfer, long callAttemptId,
                             RealtimeAudioBridge bridge, Runnable fallback,
                             Consumer<String> dtmf) {
        boolean disclosureEnabled = agent == null || agent.disclosureEnabled();
        AgentPersona agentPersona = agent != null ? agent.persona() : AgentPersona.AI_ASSISTANT;
        long companyId = records.companyIdOf(callAttemptId);
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
        session.setDtmfSender(dtmf);
        Company company = companyService.findById(companyId);
        String companyName = company != null ? company.name() : null;

        String disclosureText = null;
        if (dialogProperties.mandatoryDisclosure() && disclosureEnabled && agentPersona != AgentPersona.HUMAN_LIKE) {
            CompanyConfig companyConfig = companyConfigService.find(companyId);
            disclosureText = disclosureLine(session, companyConfig != null ? companyConfig.disclosureText() : null, companyName);
        }

        String prompt = promptFactory.build(session, companyName, disclosureText, voice, agentPersona);
        session.setTools(toolsFor(session));
        RealtimeCallConfig config = new RealtimeCallConfig(channelId, language, prompt,
                voiceFor(voice, provider), session.tools(),
                agent != null ? agent.speechEngine().pipecatStt() : null,
                agent != null ? agent.speechEngine().pipecatLlm() : null,
                agent != null ? agent.speechEngine().pipecatTts() : null,
                agent != null ? agent.llmModel() : null);
        // The catalog id only counts as the call's voice if the engine took it (voiceFor).
        session.setEngineIdentity(provider.name(), provider.resolveModel(config),
                config.voice() != null ? voice : null);
        try {
            RealtimeSession engine = provider.startSession(config,
                    new EngineListener(session, provider.outputSampleRate(), fallback, bridge));
            session.setEngine(engine);
            sessions.put(channelId, session);
            bridge.arm(engine, provider.inputSampleRate());
            engine.sendUserText(GREETING_BOOTSTRAP);
            log.info("Realtime dialog started [{}] engine={} lang={} state={}",
                    channelId, provider.name(), language, session.state());
            return true;
        } catch (Exception e) {
            log.error("[{}] realtime engine {} failed to start: {}", channelId, provider.name(), e.getMessage());
            return false;
        }
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
        TtsVoice voice = voiceCatalog.find(voiceId);
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

    private static String disclosureLine(RealtimeDialogSession s, String companyDisclosureText, String companyName) {
        String scenarioLine = Disclosure.resolve(
                s.scenario() != null ? s.scenario().disclosureText() : null, s.language(), companyName);
        if (scenarioLine != null) {
            return scenarioLine;
        }
        String companyLine = Disclosure.resolve(companyDisclosureText, s.language(), companyName);
        return companyLine != null ? companyLine : DialogPhrases.disclosure(s.language(), companyName);
    }

    /**
     * Every tool this call's scenario allows, declared once at connect.
     */
    private List<ToolCallback> toolsFor(RealtimeDialogSession s) {
        Set<String> declared = new HashSet<>();
        if (s.scenario().tools() != null) {
            s.scenario().tools().forEach(t -> declared.add(t.name()));
        }
        List<ToolCallback> callbacks = new ArrayList<>();
        if (!realtimeProperties.factsInPrompt()) {
            callbacks.addAll(List.of(MethodToolCallbackProvider.builder()
                    .toolObjects(new RealtimeFactTools(s)).build().getToolCallbacks()));
        }
        // Only for agents that asked for it: an engine offered a tool it never needs still
        // pays for the description in every request, and may call it out of curiosity.
        if (s.agent() != null && s.agent().useRag()) {
            callbacks.addAll(List.of(MethodToolCallbackProvider.builder()
                    .toolObjects(new RealtimeKnowledgeTools(s, knowledgeRetrieval))
                    .build().getToolCallbacks()));
        }
        for (ToolCallback fixed : MethodToolCallbackProvider.builder()
                .toolObjects(new DialogTools(s)).build().getToolCallbacks()) {
            String name = fixed.getToolDefinition().name();
            if (!DialogTools.HARDCODED_TOOL_NAMES.contains(name) || declared.contains(name)) {
                callbacks.add(fixed);
            }
        }
        if (s.scenario().tools() != null) {
            for (ToolDef toolDef : s.scenario().tools()) {
                if (!DialogTools.HARDCODED_TOOL_NAMES.contains(toolDef.name())) {
                    callbacks.add(ScenarioToolCallbackFactory.build(toolDef, s));
                }
            }
        }
        return callbacks;
    }

    @Override
    public DialogOutcome outcome(String channelId) {
        RealtimeDialogSession s = sessions.get(channelId);
        return s == null ? DialogOutcome.NONE : new DialogOutcome(s.disposition(), s.doNotCallReason());
    }

    @Override
    public DialogTechnicalSnapshot technicalSnapshot(String channelId) {
        RealtimeDialogSession s = sessions.get(channelId);
        if (s == null) {
            return DialogTechnicalSnapshot.NONE;
        }
        return new DialogTechnicalSnapshot(s.turnCount(), 0, 0, 0, 0, null, null, null, null,
                s.ttsVoice(), s.language(), s.engineName(), s.llmModel());
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
        log.info("Realtime dialog ended [{}] turns={} disposition={}",
                channelId, s.turnCount(), s.disposition());
        if (s.factViolations() > 0) {
            log.error("[{}] {} fact-guard violation(s) were spoken this call", channelId, s.factViolations());
        }
    }

    @Override
    public List<LiveDialogSnapshot> liveDialogs() {
        return sessions.values().stream()
                .filter(s -> !s.isEnded())
                .map(s -> new LiveDialogSnapshot(s.channelId(), s.startedAt(), s.state(), s.language(),
                        s.context() != null ? asText(s.context().fact("clientName")) : null))
                .toList();
    }

    @Override
    public OperatorSnapshot operatorSnapshot(String channelId) {
        RealtimeDialogSession s = sessions.get(channelId);
        if (s == null) {
            return null;
        }
        CallContext c = s.context();
        return new OperatorSnapshot(channelId,
                asText(c.fact("clientName")), asText(c.fact("debtAmount")), asText(c.fact("currency")),
                asText(c.fact("dueDate")), asText(c.fact("contractNumber")),
                s.state(), s.transcriptText());
    }

    private static String asText(Object value) {
        return value == null ? null : value.toString();
    }

    private void auditFacts(RealtimeDialogSession s, String text) {
        if (!dialogProperties.factGuard()) {
            return;
        }
        List<String> bad = FactGuard.violations(text, s.scenario(), s.context());
        if (bad.isEmpty()) {
            return;
        }
        int violations = s.recordFactViolation();
        metrics.factGuardSpoken();
        log.error("[{}] fact guard: figures {} were SPOKEN in {} but are not in the call facts ({}) "
                        + "— text was: {}",
                s.channelId(), bad, s.state(), s.context() != null ? s.context().facts() : null, text);
        records.recordError(s.callAttemptId(),
                "fact guard: spoken figures not in call facts: " + String.join(", ", bad));

        int limit = dialogProperties.factViolationEscalateAfter();
        if (limit <= 0 || violations < limit || s.isEnded()) {
            return;
        }
        log.error("[{}] escalating to an operator after {} spoken fact violation(s)",
                s.channelId(), violations);
        s.end(Disposition.TRANSFERRED);
    }

    private void recordLine(RealtimeDialogSession s, String role, String text) {
        s.addTranscriptLine(role, text);
        int offsetMs = (int) (Instant.now().toEpochMilli() - s.startedAt().toEpochMilli());
        records.addTranscript(s.callAttemptId(), role, text, s.state(), offsetMs, null);
    }

    /** Carries one engine's output into the call: its audio, its words, its tool calls. */
    private final class EngineListener implements RealtimeListener {

        private final RealtimeDialogSession session;
        /**
         * Carries the anti-aliasing filter across the engine's chunks: resampling each one
         * on its own put a click at every chunk boundary (StreamingDownsampler).
         */
        private final StreamingDownsampler downsampler;
        /** Runs the call on the cascade pipeline instead; null leaves hanging up as the only option. */
        private final Runnable fallback;
        /**
         * The caller's side of the line. Armed as soon as the session is open, but only
         * opened once the engine has started speaking — {@link RealtimeAudioBridge#open()}
         * says why.
         */
        private final RealtimeAudioBridge bridge;

        private EngineListener(RealtimeDialogSession session, int engineRate, Runnable fallback,
                               RealtimeAudioBridge bridge) {
            this.session = session;
            this.downsampler = engineRate == 24000
                    ? StreamingDownsampler.from24kTo8k()
                    : StreamingDownsampler.from16kTo8k();
            this.fallback = fallback;
            this.bridge = bridge;
        }

        @Override
        public void onBotAudio(short[] pcm) {
            bridge.open();
            short[] telephone = downsampler.push(pcm, pcm.length);
            if (telephone.length > 0) {
                session.endpoint().enqueuePcm(telephone);
            }
        }

        @Override
        public void onInputTranscript(String text, boolean isFinal) {
            if (isFinal) {
                recordLine(session, "CLIENT", text);
                log.info("[{}] FINAL: {}", session.channelId(), text);
            }
        }

        @Override
        public void onOutputTranscript(String text) {
            recordLine(session, "AGENT", text);
            log.info("[{}] AGENT: {}", session.channelId(), text);
            auditFacts(session, text);
        }

        @Override
        public void onInterrupted() {
            session.endpoint().flushPlayback();
            log.debug("[{}] caller barged in", session.channelId());
        }

        @Override
        public void onTurnComplete() {
            // Also here, not only on audio: a first turn that only called a tool would
            // otherwise leave the caller unheard for the rest of the call.
            bridge.open();
            session.countTurn();
            if (session.isEnded()) {
                toolExecutor.execute(() -> finishWhenSpoken(session));
            }
        }

        @Override
        public void onToolCall(String callId, String name, String argumentsJson) {
            toolExecutor.execute(() -> runTool(session, callId, name, argumentsJson));
        }

        @Override
        public void onClosed(Throwable cause) {
            if (cause == null) {
                return;
            }
            // Reached only once the provider has exhausted its own recovery — Gemini Live,
            // for one, resumes onto a fresh connection first and only reports here when
            // even that failed. The caller is still on the line either way, so the cascade
            // pipeline takes over rather than the call being dropped on them.
            log.warn("[{}] realtime engine closed: {}", session.channelId(), cause.getMessage());
            sessions.remove(session.channelId());
            toolExecutor.execute(fallback != null ? fallback : session.hangup());
        }
    }

    private void runTool(RealtimeDialogSession s, String callId, String name, String argumentsJson) {
        String result;
        try {
            ToolCallback callback = s.tools().stream()
                    .filter(t -> t.getToolDefinition().name().equals(name))
                    .findFirst()
                    .orElse(null);
            if (callback == null) {
                log.warn("[{}] engine called unknown tool {}", s.channelId(), name);
                result = "XATO: bunday tool yo'q";
            } else {
                result = callback.call(argumentsJson);
                log.info("[{}] tool {} -> {}", s.channelId(), name, result);
            }
        } catch (Exception e) {
            log.warn("[{}] tool {} failed: {}", s.channelId(), name, e.getMessage());
            result = "XATO: " + e.getMessage();
        }
        RealtimeSession engine = s.engine();
        if (engine != null) {
            engine.sendToolResult(callId, result);
        }
    }

    private void finishWhenSpoken(RealtimeDialogSession s) {
        long deadline = System.nanoTime() + MAX_DRAIN.toNanos();
        try {
            while (s.endpoint().isPlaying() && System.nanoTime() < deadline) {
                Thread.sleep(100);
            }
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        Runnable action = (s.disposition() == Disposition.TRANSFERRED && s.transfer() != null)
                ? s.transfer() : s.hangup();
        if (action != null) {
            action.run();
        }
    }

    @PreDestroy
    public void shutdown() {
        toolExecutor.shutdownNow();
    }
}
