package uz.murodjon.uysotvoice.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.agent.rtp.RtpEndpoint;
import uz.murodjon.uysotvoice.aimodel.domain.EffectiveAiModelConfig;
import uz.murodjon.uysotvoice.aimodel.service.AiModelConfigService;
import uz.murodjon.uysotvoice.callrecord.service.CallRecordService;
import uz.murodjon.uysotvoice.company.dto.Company;
import uz.murodjon.uysotvoice.company.dto.CompanyConfig;
import uz.murodjon.uysotvoice.company.service.CompanyConfigService;
import uz.murodjon.uysotvoice.company.service.CompanyService;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioDefinition;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;
import uz.murodjon.uysotvoice.voice.dto.EffectiveVoiceSettings;
import uz.murodjon.uysotvoice.voice.service.VoiceSettingsService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * The cascade pipeline's conversation engine (PROJECT.md §4, §7.1, Stage 7): STT → LLM
 * → TTS, one stage per collaborator.
 *
 * <pre>
 *   RtpEndpoint ─audio→ SttStreamBridge ─text→ ClientInputGate ─turn→ TurnRunner
 *                                                                        │
 *   RtpEndpoint ←audio─ SpeechOutput ←──────────────sentences─────────────┘
 * </pre>
 *
 * <p>What is left here is what belongs to the <em>call</em> rather than to any one stage:
 * the {@link DialogSession} registry, the resources a session is opened with, the §11.1
 * disclosure the call has to open with, and the read-only views the rest of the system
 * takes of a live conversation ({@link CallDialog}).
 *
 * <ul>
 *   <li>{@link ClientInputGate} — what the recognizer produced, and whether it is worth a turn
 *   <li>{@link TurnRunner} — the LLM turn, its tools, and everything written before or after one
 *   <li>{@link SpeechOutput} — text to audio the caller hears, and the guards on the way
 *   <li>{@link SilenceWatchdogRunner} — a caller who has stopped answering
 * </ul>
 *
 * <p>Every stage takes a {@link DialogSession} rather than a channel id: this class owns
 * the registry, so a stage never has to resolve a call, and a call that has already been
 * torn down is stopped here once instead of in four places.
 *
 * <p>Non-fatal without credentials: if no LLM ChatModel is available (no
 * {@code GEMINI_API_KEY}), the app still runs and dialog is simply disabled.
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

    /**
     * Registers a conversation for {@code channelId} and drives the opening greeting.
     * No-op if dialog is disabled or the LLM is unavailable.
     *
     * @param ttsVoice catalog id of the voice this call speaks with (§2.5), or null for
     *                 the configured routing
     * @param scenario the scenario this call runs (ROADMAP A.3), resolved once by the
     *                 caller and reused for the whole call — never re-fetched mid-call,
     *                 so an edit to the scenario never changes a call already in flight
     * @param disclosureEnabled whether this call's campaign wants the §11.1 disclosure
     *                 spoken; still gated by {@code voice-agent.dialog.mandatory-
     *                 disclosure} as a hard kill-switch (see {@link #speakDisclosure})
     * @param hangup invoked once the conversation ends, to hang up the channel
     */
    public void startCall(String channelId, RtpEndpoint endpoint, CallContext context,
                          ScenarioDefinition scenario, String language, String ttsVoice, boolean disclosureEnabled,
                          Runnable hangup, Runnable transfer, long callAttemptId) {
        startCall(channelId, endpoint, context, scenario, language, ttsVoice, disclosureEnabled, hangup, transfer, callAttemptId, true);
    }

    public void startCall(String channelId, RtpEndpoint endpoint, CallContext context,
                          ScenarioDefinition scenario, String language, String ttsVoice, boolean disclosureEnabled,
                          Runnable hangup, Runnable transfer, long callAttemptId, boolean emotionAdaptiveVoice) {
        if (!available()) {
            log.debug("Dialog not started for {} (engine unavailable)", channelId);
            return;
        }
        // Resolved once per call, not per turn: a company's overrides never change
        // mid-call, and this is a DB round trip on the ARI thread we do not want to
        // repeat on every one of a call's turns.
        long companyId = records.companyIdOf(callAttemptId);
        EffectiveAiModelConfig aiModel = aiModelConfigService.findEffectiveByCompanyId(companyId);
        EffectiveVoiceSettings voiceSettings = voiceSettingsService.effective(companyId);
        // Whose name the disclosure is spoken in (§11.1) — the platform is multi-tenant,
        // so it cannot be a constant in the phrase itself. A company that has since been
        // deleted leaves the disclosure naming nobody rather than failing the call.
        Company company = companyService.findById(companyId);
        String companyName = company != null ? company.name() : null;
        // ...and in whose words: the disclosure is a company setting (company config),
        // since what it says is that this company is calling you with a machine and
        // recording it. A company with no config row leaves it to the platform's wording.
        CompanyConfig companyConfig = companyConfigService.find(companyId);
        String companyDisclosure = companyConfig != null ? companyConfig.disclosureText() : null;
        DialogSession session = new DialogSession(channelId, language, ttsVoice, context, scenario, endpoint,
                hangup, transfer, callAttemptId, watchdogRunner.createWatchdog(), disclosureEnabled, companyName,
                companyDisclosure, aiModel, voiceSettings, emotionAdaptiveVoice);
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
                // The caller spoke over the disclosure — they have asked a question or
                // said "who is this", and the scripted greeting would talk straight over
                // the answer. Let their own turn drive the call instead; if the barge-in
                // turns out to have been noise, the resume below still opens the call.
                log.info("[{}] caller spoke over the disclosure — greeting deferred to their turn",
                        channelId);
                turnRunner.scheduleFalseInterruptionCheck(session, session.turnCount(),
                        () -> turnRunner.startGreeting(session));
                return;
            }
            turnRunner.startGreeting(session);
        });
    }

    /**
     * Hold the first word back until the caller can actually hear it. "Answered" means
     * the far end sent a 200 OK — the handset still needs a few hundred milliseconds to
     * open its audio path, and the person is still lifting it to their ear. Greeting
     * into that gap loses the front of the sentence: the caller hears "…alaykum" and
     * the "assalom" that carried it was spoken to nobody.
     *
     * @return false when the call ended (or the wait was interrupted) during the pause,
     *         meaning nothing should be spoken
     */
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

    /**
     * Speak the mandatory disclosure — this is an automated system and the call is
     * being recorded (§11.1) — before the model gets a turn.
     *
     * <p>The requirement is legal, and the system prompt only <em>asks</em> the model
     * to say it; a model that skips or paraphrases it away puts the call on the wrong
     * side of §11. Saying it from code makes it unconditional, and the prompt then
     * tells the model not to repeat it.
     *
     * <p>Gated by two independent switches: the global {@code mandatory-disclosure}
     * config is an ops-level kill-switch across every call, and {@code
     * DialogSession.disclosureEnabled()} is the calling campaign's own choice (§10.6).
     * Both must allow it.
     */
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

    /** Feeds a final client transcript into the conversation as the next turn. */
    public void onClientFinal(String channelId, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        DialogSession session = sessions.get(channelId);
        if (session == null || session.isEnded()) {
            return;
        }
        inputGate.onFinal(session, text);
    }

    /**
     * An interim transcript: what the recognizer currently thinks the caller is saying,
     * before it has committed to it.
     *
     * <p>Straight to {@link TurnRunner#speculate} rather than through
     * {@link ClientInputGate}, because an interim is never treated as something the
     * caller said — nothing is spoken from one and no turn is started. All it does is
     * begin writing a reply during the endpointing silence the turn would otherwise
     * spend waiting (§1.3).
     */
    public void onClientInterim(String channelId, String text) {
        DialogSession session = sessions.get(channelId);
        if (session == null) {
            return;
        }
        turnRunner.speculate(session, text);
    }

    /**
     * Barge-in (§7.2): the client started speaking, so silence the bot.
     *
     * @return whether there was in fact a reply to interrupt; the detector uses this to
     *         decide whether it has spent its arming (see {@code VadStream})
     */
    public boolean notifyBargeIn(String channelId) {
        DialogSession session = sessions.get(channelId);
        if (session == null || session.isEnded()) {
            return false;
        }
        return inputGate.onBargeIn(session);
    }

    /** Whether this engine is the one running {@code channelId} — see {@link DialogRouter}. */
    @Override
    public boolean owns(String channelId) {
        return sessions.containsKey(channelId);
    }

    /**
     * What the dialog recorded for {@code channelId}. Read before {@link #endCall}
     * drops the session — afterwards the outcome is unrecoverable.
     */
    @Override
    public DialogOutcome outcome(String channelId) {
        DialogSession session = sessions.get(channelId);
        return session != null
                ? new DialogOutcome(session.disposition(), session.doNotCallReason())
                : DialogOutcome.NONE;
    }

    /**
     * What the dialog accumulated for {@code channelId}'s "Texnik" tab (§10.5). Read
     * before {@link #endCall} drops the session, same as {@link #outcome}.
     */
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

    /**
     * Marks a call as an answering machine (PROJECT.md §8.6) and stops the conversation.
     *
     * <p>Called from the AMD detector while the bot is still on its opening line. The
     * disposition has to be recorded on the session, because that is what teardown reads
     * to set {@code call_attempt.disposition} and to decide the target's retry — a call
     * that only got hung up would come back as an ordinary unanswered attempt.
     *
     * @return whether a live conversation was actually ended here
     */
    @Override
    public boolean notifyVoicemail(String channelId) {
        DialogSession session = sessions.get(channelId);
        if (session == null || session.isEnded()) {
            return false;
        }
        // Silence the greeting mid-word: there is nobody to hear the rest, and every
        // sentence still queued is TTS that has already been paid for.
        session.setCancelled(true);
        session.endpoint().flushPlayback();
        session.end(Disposition.VOICEMAIL);
        log.info("[{}] answering machine detected — ending call as VOICEMAIL", channelId);
        return true;
    }

    /** Drops the conversation state when the call tears down. */
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
        // A reply still being written for a call that has hung up: nobody will hear it,
        // and left subscribed it would generate (and bill) to the end.
        session.discardSpeculation();
        if (session.factViolations() > 0) {
            // Worth a line at call level: a single blocked sentence is a model slip, a
            // handful in one call means the prompt or the facts are wrong.
            log.warn("[{}] fact guard blocked {} sentence(s) this call",
                    channelId, session.factViolations());
        }
    }

    /**
     * Every call still in conversation, for the "Jonli qo'ng'iroqlar" list
     * (§10.2/§10.3 UI-DESIGN.md). Excludes a session already marked {@code ended} —
     * such a session is draining its farewell audio and tearing down, not live.
     */
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

    /** Live context for the operator screen after a transfer (null if unknown). */
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

    /**
     * A well-known fact by name, as text — {@code null} if the call has no context or
     * the bound scenario's factSchema doesn't declare that fact (ROADMAP A.3: not every
     * scenario has a debtor identity, so these UI fields are best-effort).
     */
    private static String asString(CallContext c, String factName) {
        if (c == null) {
            return null;
        }
        Object value = c.fact(factName);
        return value == null ? null : value.toString();
    }
}
