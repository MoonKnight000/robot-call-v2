package uz.murodjon.uysotvoice.agent.dialog;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.agent.audio.Resampler;
import uz.murodjon.uysotvoice.agent.metrics.VoiceMetrics;
import uz.murodjon.uysotvoice.agent.realtime.RealtimeAudioBridge;
import uz.murodjon.uysotvoice.agent.realtime.RealtimeCallConfig;
import uz.murodjon.uysotvoice.agent.realtime.RealtimeListener;
import uz.murodjon.uysotvoice.agent.realtime.RealtimeProperties;
import uz.murodjon.uysotvoice.agent.realtime.RealtimeProvider;
import uz.murodjon.uysotvoice.agent.realtime.RealtimeProviderRegistry;
import uz.murodjon.uysotvoice.agent.realtime.RealtimeSession;
import uz.murodjon.uysotvoice.agent.rtp.RtpEndpoint;
import uz.murodjon.uysotvoice.agent.tts.TtsRouter;
import uz.murodjon.uysotvoice.callrecord.service.CallRecordService;
import uz.murodjon.uysotvoice.company.dto.Company;
import uz.murodjon.uysotvoice.company.dto.CompanyConfig;
import uz.murodjon.uysotvoice.company.service.CompanyConfigService;
import uz.murodjon.uysotvoice.company.service.CompanyService;
import uz.murodjon.uysotvoice.engine.service.EngineConfigService;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioDefinition;
import uz.murodjon.uysotvoice.scenario.dto.ToolDef;
import uz.murodjon.uysotvoice.shared.dialog.Disclosure;
import uz.murodjon.uysotvoice.shared.dialog.DialogPhrases;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;
import uz.murodjon.uysotvoice.voice.service.VoiceSettingsService;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
 * <p>What is shared is shared properly: the tools are the same {@link DialogTools} and
 * {@link ScenarioToolCallbackFactory} objects the cascade uses, through
 * {@link DialogOutcomeSink}, so the §4.4 code guardrails exist once. The opening §11.1
 * disclosure is still spoken from TTS rather than by the engine — its wording is a legal
 * obligation, and an engine asked to say it "in its own words" is an engine that will
 * eventually say something else.
 *
 * <p>The §4.4 money guardrail is two layers here, and neither is the cascade's. The
 * engine is not given the call's figures at all — it fetches each one as it says it
 * ({@link RealtimeFactTools}), so there is nothing in its context to misremember — and
 * whatever it does say is checked against the facts afterwards ({@link #auditFacts}),
 * because a fabricated figure is still possible and cannot be caught any earlier.
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
    private final EngineConfigService engineConfigService;
    private final RealtimeSystemPromptFactory promptFactory;
    private final CallRecordService records;
    private final CompanyService companyService;
    private final CompanyConfigService companyConfigService;
    private final TtsRouter ttsRouter;
    private final VoiceSettingsService voiceSettingsService;
    private final DialogProperties props;
    private final RealtimeProperties realtimeProperties;
    private final VoiceMetrics metrics;

    private final Map<String, RealtimeDialogSession> sessions = new ConcurrentHashMap<>();
    /**
     * Tool bodies hit the database and the CRM, and they arrive on the engine's network
     * thread — the same thread its next audio frame comes in on. Virtual threads because
     * the work is all waiting, and there is one per tool call, not one per call.
     */
    private final ExecutorService toolExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public RealtimeDialogEngine(RealtimeProviderRegistry registry, EngineConfigService engineConfigService,
                                RealtimeSystemPromptFactory promptFactory, CallRecordService records,
                                CompanyService companyService, CompanyConfigService companyConfigService,
                                TtsRouter ttsRouter, VoiceSettingsService voiceSettingsService,
                                DialogProperties props, RealtimeProperties realtimeProperties,
                                VoiceMetrics metrics) {
        this.registry = registry;
        this.engineConfigService = engineConfigService;
        this.promptFactory = promptFactory;
        this.records = records;
        this.companyService = companyService;
        this.companyConfigService = companyConfigService;
        this.ttsRouter = ttsRouter;
        this.voiceSettingsService = voiceSettingsService;
        this.props = props;
        this.realtimeProperties = realtimeProperties;
        this.metrics = metrics;
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
     * <p>Same shape as {@link DialogEngine#startCall}, plus the {@code bridge} the caller
     * already registered as one of the call's audio listeners: it is armed here, once
     * there is a session for it to feed. Returns whether the call is actually running on
     * an engine — {@code false} means it is not, and the caller must not leave the line
     * open in silence.
     */
    public boolean startCall(String channelId, RtpEndpoint endpoint, CallContext context,
                             ScenarioDefinition scenario, String language, boolean disclosureEnabled,
                             Runnable hangup, Runnable transfer, long callAttemptId,
                             RealtimeAudioBridge bridge) {
        long companyId = records.companyIdOf(callAttemptId);
        RealtimeProvider provider = registry.findForCall(
                engineConfigService.findEffectiveByCompanyId(companyId).realtimeProvider());
        if (provider == null) {
            log.error("[{}] REALTIME was selected but no engine resolved — call cannot start", channelId);
            return false;
        }

        RealtimeDialogSession session = new RealtimeDialogSession(channelId, language, callAttemptId,
                scenario, context, endpoint, hangup, transfer);
        Company company = companyService.findById(companyId);
        String companyName = company != null ? company.name() : null;

        // Spoken before the engine is told anything, so its instructions can say the
        // notice is already out of the way (§11.1) — and so a caller who hangs up during
        // it never costs a session at all.
        boolean disclosureSpoken = speakDisclosure(session, companyId, companyName, disclosureEnabled);

        String prompt = promptFactory.build(session, companyName, disclosureSpoken);
        session.setTools(toolsFor(session));
        try {
            RealtimeSession engine = provider.startSession(
                    new RealtimeCallConfig(channelId, language, prompt, null, session.tools()),
                    new EngineListener(session, provider.outputSampleRate()));
            session.setEngine(engine);
            sessions.put(channelId, session);
            // Armed before the nudge: the engine answers it within a few hundred
            // milliseconds, and the caller may well be talking over the disclosure already.
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
     * Speak the §11.1 notice through TTS, exactly as the cascade pipeline does.
     *
     * @return whether it was actually spoken — false leaves the engine free to open with
     *         its own greeting
     */
    private boolean speakDisclosure(RealtimeDialogSession s, long companyId, String companyName,
                                    boolean disclosureEnabled) {
        if (!props.mandatoryDisclosure() || !disclosureEnabled) {
            return false;
        }
        CompanyConfig companyConfig = companyConfigService.find(companyId);
        String line = disclosureLine(s, companyConfig != null ? companyConfig.disclosureText() : null, companyName);
        try {
            s.endpoint().playPcm(ttsRouter.synthesize(line, s.language(), null,
                    voiceSettingsService.effective(companyId)));
            recordLine(s, "AGENT", line);
            log.info("[{}] disclosure: {}", s.channelId(), line);
            return true;
        } catch (Exception e) {
            // A call that opens without the notice is worse than one that opens late, but
            // far better than one that never connects; the engine then greets normally.
            log.warn("[{}] disclosure could not be spoken: {}", s.channelId(), e.getMessage());
            return false;
        }
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
     *
     * <p>Unlike {@link DialogEngine#toolsFor}, the set is not narrowed to the current
     * stage: the declarations are sent once and never revised, so a tool left out here is
     * one the engine can never call, however far the conversation travels.
     */
    private List<ToolCallback> toolsFor(RealtimeDialogSession s) {
        Set<String> declared = new HashSet<>();
        if (s.scenario().tools() != null) {
            s.scenario().tools().forEach(t -> declared.add(t.name()));
        }
        List<ToolCallback> callbacks = new ArrayList<>();
        // getCallFact is how the engine learns any figure at all when the values are kept
        // out of its instructions (§4.4) — without it the call would have nothing to say.
        if (!realtimeProperties.factsInPrompt()) {
            callbacks.addAll(List.of(MethodToolCallbackProvider.builder()
                    .toolObjects(new RealtimeFactTools(s)).build().getToolCallbacks()));
        }
        for (ToolCallback fixed : MethodToolCallbackProvider.builder()
                .toolObjects(new DialogTools(s)).build().getToolCallbacks()) {
            String name = fixed.getToolDefinition().name();
            // recordPaymentPromise/recordRefusalReason are scenario-specific; the rest are
            // universal — same rule as the cascade pipeline applies.
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

    /**
     * The realtime call's "Texnik" tab (§10.5). Token counts and LLM latency stay zero:
     * the engine bills by session time rather than tokens, and there is no LLM round trip
     * to time — a fabricated number here would read as a measurement.
     */
    @Override
    public DialogTechnicalSnapshot technicalSnapshot(String channelId) {
        RealtimeDialogSession s = sessions.get(channelId);
        if (s == null) {
            return DialogTechnicalSnapshot.NONE;
        }
        return new DialogTechnicalSnapshot(s.turnCount(), 0, 0, 0, null, null, null, null, null, s.language());
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
        // Nothing to persist here: what the call produced is written by CallFinalizer,
        // which summarizes the transcript against the scenario's outcomeSchema — and the
        // transcript is complete, because every line either side spoke was recorded as it
        // happened. The same route the cascade pipeline takes.
        RealtimeSession engine = s.engine();
        if (engine != null) {
            engine.close();
        }
        log.info("Realtime dialog ended [{}] turns={} disposition={}",
                channelId, s.turnCount(), s.disposition());
        if (s.factViolations() > 0) {
            // Worth a line at call level, and a louder one than the cascade's: every one
            // of these was heard by the caller.
            log.error("[{}] {} fact-guard violation(s) were spoken this call", channelId, s.factViolations());
        }
    }

    /** Excludes a session already marked ended — it is draining its farewell, not live. */
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

    /**
     * Check what the engine just said against the call's facts (§4.4) — after it has been
     * said, because there is no earlier moment to check it at.
     *
     * <p>This is the whole difference between the two pipelines' guardrails, and it is
     * worth being plain about: the cascade engine sees the sentence as text before
     * anything is synthesized, so it withholds it and the caller never hears it. A
     * speech-to-speech engine produces audio directly; the transcript of that audio is
     * the first text anyone gets, and by then the caller has heard it. Nothing can be
     * prevented here, so what happens instead is the response a wrongly-billed caller
     * actually needs: the incident is logged, the attempt is flagged, and a person takes
     * the call over.
     *
     * <p>The escalation is not performed here. Marking the conversation TRANSFERRED is
     * enough: this runs as the turn settles, and the {@code turnComplete} immediately
     * behind it takes the ended session through {@link #finishWhenSpoken}, which drains
     * the sentence already playing before handing the line over. Transferring on the spot
     * would cut the engine off mid-word in front of a caller who is already confused.
     */
    private void auditFacts(RealtimeDialogSession s, String text) {
        if (!props.factGuard()) {
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
        // On the attempt row, so the incident is visible to whoever reviews the call
        // rather than only to whoever reads the logs that day.
        records.recordError(s.callAttemptId(),
                "fact guard: spoken figures not in call facts: " + String.join(", ", bad));

        int limit = props.factViolationEscalateAfter();
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
        private final int engineRate;

        private EngineListener(RealtimeDialogSession session, int engineRate) {
            this.session = session;
            this.engineRate = engineRate;
        }

        @Override
        public void onBotAudio(short[] pcm) {
            short[] telephone = engineRate == 24000
                    ? Resampler.downsample24kTo8k(pcm, pcm.length)
                    : Resampler.downsample16kTo8k(pcm, pcm.length);
            // Appended, not played over: the engine streams a sentence as a run of chunks,
            // and playPcm would drop each one as the next arrived.
            session.endpoint().enqueuePcm(telephone);
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
            // The engine has stopped mid-sentence; what is still queued for playback is a
            // sentence it has abandoned, and the caller is already talking over it.
            session.endpoint().flushPlayback();
            log.debug("[{}] caller barged in", session.channelId());
        }

        @Override
        public void onTurnComplete() {
            session.countTurn();
            // A tool ended the conversation, so this is the goodbye that followed it —
            // hang up once it has actually been played out rather than cutting it off.
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
            if (cause != null) {
                log.warn("[{}] realtime engine closed: {}", session.channelId(), cause.getMessage());
                // The engine is gone and cannot be replaced without losing the whole
                // conversation, so the call ends rather than continuing in silence.
                // Off this thread: hanging up talks to Asterisk over HTTP, and this is the
                // provider's network thread.
                toolExecutor.execute(session.hangup());
            }
        }
    }

    /**
     * Run one tool the engine asked for and hand back its result. A transfer is acted on
     * here rather than at teardown: the caller asked for a person and is waiting.
     */
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

    /**
     * Waits for the queued audio to drain, then ends the call the way its outcome asks
     * for: a human operator if it was TRANSFERRED (§11.6), otherwise a hangup.
     *
     * <p>Polls rather than sleeping for a computed duration — the engine streams its
     * goodbye in chunks and the total length is not known when the last one arrives, so
     * anything precomputed would cut the farewell off.
     */
    private void finishWhenSpoken(RealtimeDialogSession s) {
        long deadline = System.nanoTime() + MAX_DRAIN.toNanos();
        try {
            while (s.endpoint().isPlaying() && System.nanoTime() < deadline) {
                Thread.sleep(100);
            }
            Thread.sleep(500); // let the tail reach the caller before the channel drops
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
