package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.metrics.CallMetrics;
import uz.murodjon.robotcallv2.agent.metrics.DialogMetrics;
import uz.murodjon.robotcallv2.agent.stt.SttProperties;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.enums.InterruptionSensitivity;
import uz.murodjon.robotcallv2.aiagent.domain.enums.VoicemailAction;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.util.List;
import java.util.Locale;

/**
 * What the recognizer hands over, and whether it is worth a turn (PROJECT.md §7.2, MASTER_ROADMAP.md §4).
 * The first box after the STT stream: everything here decides between the caller's
 * words and the LLM, and nothing here talks to the model itself.
 *
 * <p>Handles:
 * <ul>
 *   <li>Echo suppression (caller repeating the bot verbatim over speakerphone)
 *   <li>Answering Machine / Voicemail phrase detection on initial turns
 *   <li>Backchannel agreements ("ha", "aha") while cut-off replies resume
 *   <li>Sign-offs ("rahmat", "спасибо") once the outcome is recorded — closed, not answered
 *   <li>Barge-in / fast playback flush
 *   <li>Answering the caller in the language they actually speak (LanguageDetector)
 * </ul>
 */
@Component
public class ClientInputGate {

    private static final Logger log = LoggerFactory.getLogger(ClientInputGate.class);

    /**
     * Carrier announcements that the subscriber cannot be reached — phone off, out of
     * coverage. Only automated greetings, never conversational phrases a real person might
     * say (e.g. "keyinroq qiling"). Nothing records after these, so no message is left.
     */
    private static final List<String> UNREACHABLE_PHRASES = List.of(
            "apparati o'chirilgan", "xizmat doirasidan tashqarida", "telefon o'chirilgan",
            "chaqirilayotgan abonent apparati", "tarmoq doirasidan tashqarida", "tarmoqda yo'q",
            "apparati o'chiq", "chaqiruv qabul qilinmadi",
            "абонент недоступен", "аппарат абонента выключен", "находится вне зоны",
            "данный вид связи недоступен", "номер временно заблокирован", "временно недоступен",
            "currently unavailable", "switched off"
    );

    /**
     * An answering machine or voicemail box inviting a message — the only case where
     * leaving one ({@code VoicemailAction.LEAVE_MESSAGE}) reaches anybody.
     */
    private static final List<String> MESSAGE_PROMPT_PHRASES = List.of(
            "ovozli xabar qoldiring", "signal ovozidan so'ng", "signal ovozidan keyin",
            "boshqa raqamga yo'naltirilgan", "bip ovozidan keyin", "bipdan so'ng",
            "оставьте сообщение", "после сигнала", "после звукового сигнала", "автоответчик",
            "leave a message after the tone"
    );

    /**
     * Carrier announcements that the dialled number does not exist — whole phrases, so a
     * person saying a number is wrong in conversation is never matched.
     */
    private static final List<String> NO_SUCH_NUMBER_PHRASES = List.of(
            "raqam mavjud emas", "raqam noto'g'ri terilgan", "noto'g'ri terilgan",
            "номер не существует", "неправильно набран", "набран неверно", "номер не обслуживается",
            "not in service", "does not exist"
    );

    /**
     * How long a paused bot waits for the caller's words before deciding it was a cough
     * or a breath. Long enough for the recognizer's first interim, short enough that the
     * pause reads as the bot letting someone in rather than as a dropped line.
     */
    private static final long BARGE_IN_CONFIRM_MS = 1200;

    private final DialogProperties dialogProperties;
    private final SttProperties sttProperties;
    private final LanguageDetector languageDetector;
    private final DialogMetrics metrics;
    private final DialogTranscript transcript;
    private final TurnRunner turnRunner;
    private final DialogExecutors executors;
    private final DialogTuningProperties dialogTuning;
    private final LlmTurnClient llmTurnClient;
    private final VoicemailDrop voicemailDrop;
    private final CallMetrics callMetrics;
    private final FloorLearning floorLearning;

    public ClientInputGate(DialogProperties dialogProperties, SttProperties sttProperties, LanguageDetector languageDetector,
                           DialogMetrics metrics, DialogTranscript transcript,
                           TurnRunner turnRunner, DialogExecutors executors,
                           DialogTuningProperties dialogTuning, LlmTurnClient llmTurnClient,
                           VoicemailDrop voicemailDrop,
                           CallMetrics callMetrics,
                           FloorLearning floorLearning) {
        this.floorLearning = floorLearning;
        this.callMetrics = callMetrics;
        this.dialogTuning = dialogTuning;
        this.llmTurnClient = llmTurnClient;
        this.voicemailDrop = voicemailDrop;
        this.dialogProperties = dialogProperties;
        this.sttProperties = sttProperties;
        this.languageDetector = languageDetector;
        this.metrics = metrics;
        this.transcript = transcript;
        this.turnRunner = turnRunner;
        this.executors = executors;
    }

    /** Feeds a final client transcript into the conversation as the next turn. */
    public void onFinal(DialogSession s, String text) {
        // Settled here, before anything is decided, so the no-words timer cannot resume
        // the bot underneath the decision.
        if (s.isBargeInPending() && s.settleBargeIn()) {
            if (isEchoOfBot(s, text)) {
                resumeBot(s, "echo of the bot");
            } else if (saidEnoughToInterrupt(s, text)) {
                confirmBargeIn(s, true);
                handleFinal(s, text, true);
                return;
            } else {
                // A word or two over the bot: "ha" is agreement over one line and the
                // answer to another. The model reads it against the line it was said over.
                executors.submit(() -> decidePausedBargeIn(s, text));
                return;
            }
        }
        handleFinal(s, text, false);
    }

    /** The bot is paused on a short caller utterance; the model says whether it yields. */
    private void decidePausedBargeIn(DialogSession s, String text) {
        if (s.isEnded()) {
            return;
        }
        if (isTakingTheFloor(s, text)) {
            confirmBargeIn(s, true);
            handleFinal(s, text, true);
            return;
        }
        transcript.publishClientLine(s, text);
        metrics.backchannelIgnored();
        resumeBot(s, "the caller let the bot carry on");
        log.debug("[{}] carried on over: {}", s.channelId(), text);
    }

    /**
     * @param floorDecided whether this utterance has already been judged an interruption,
     *                     so it is not put to the model a second time
     */
    private void handleFinal(DialogSession s, String text, boolean floorDecided) {
        if (isEchoOfBot(s, text)) {
            // The far end has no echo cancellation and the recognizer has just handed us
            // the bot's own sentence back. Answering it starts a loop the caller cannot
            // get a word into — the bot replies to itself, at length, about nothing.
            metrics.echoSuppressed();
            log.warn("[{}] dropped a caller transcript that echoes the bot's own line: {}",
                    s.channelId(), text);
            return;
        }

        // Said, so the operator watching the call should see it — whether or not the bot
        // treats it as its cue to speak.
        transcript.publishClientLine(s, text);
        s.touchActivity();

        // A carrier saying the number does not exist: nobody will ever answer it, so it is
        // closed as a wrong number — never retried, and no voicemail action applies.
        if (!s.isInbound() && s.turnCount() <= 2 && containsAny(text, NO_SUCH_NUMBER_PHRASES)) {
            log.info("[{}] carrier announced the number does not exist — closing as WRONG_NUMBER", s.channelId());
            log.debug("[{}] announcement transcript: '{}'", s.channelId(), text);
            s.setDisposition(Disposition.WRONG_NUMBER);
            s.end(Disposition.WRONG_NUMBER);
            if (s.hangup() != null) {
                s.hangup().run();
            }
            return;
        }

        // Check for Voicemail / Answering machine transcript in early turns (turn <= 2) on outbound calls only
        boolean takesMessages = containsAny(text, MESSAGE_PROMPT_PHRASES);
        if (!s.isInbound() && s.turnCount() <= 2 && (takesMessages || containsAny(text, UNREACHABLE_PHRASES))) {
            log.info("[{}] Voicemail phrase detected in client transcript", s.channelId());
            callMetrics.voicemailDetected("transcript");
            log.debug("[{}] voicemail transcript: '{}'", s.channelId(), text);
            AiAgent agent = s.agent();
            // HANGUP only when there is no agent at all: it has no message to leave.
            VoicemailAction action = (agent != null)
                    ? agent.callBehaviour().voicemailAction()
                    : VoicemailAction.HANGUP;

            if (action == VoicemailAction.IGNORE) {
                // The session must stay alive for this to mean anything — ending it first
                // and then "continuing" left the call closing under the agent's feet.
                log.info("[{}] Agent voicemailAction is IGNORE - continuing call despite voicemail phrase", s.channelId());
            } else {
                s.setDisposition(Disposition.VOICEMAIL);
                String message = agent != null ? agent.callBehaviour().voicemailMessage() : null;
                if (action.leavesMessage() && takesMessages && message != null && !message.isBlank()) {
                    log.info("[{}] Agent voicemailAction is LEAVE_MESSAGE - leaving the message after the beep", s.channelId());
                    voicemailDrop.start(s, message);
                } else {
                    s.end(Disposition.VOICEMAIL);
                    if (s.hangup() != null) {
                        s.hangup().run();
                    }
                }
                return;
            }
        }

        adaptLanguage(s, text);

        // When the bot was paused waiting on a barge-in (MEDIUM/LOW sensitivity), the caller's
        // complete utterance has now arrived. If it was only a listener sound ("aha", "tushunarli"),
        // the bot carries on without an LLM round-trip.
        if (s.isBargeInPending()) {
            if (FloorRules.isBackchannel(text)) {
                if (s.settleBargeIn()) {
                    metrics.backchannelIgnored();
                    resumeBot(s, "backchannel: " + text);
                    log.info("[{}] caller backchannel '{}' while bot was paused — carrying on", s.channelId(), text);
                }
                return;
            }
            if (saidEnoughToInterrupt(s, text) || Boolean.TRUE.equals(FloorRules.decide(text))) {
                if (s.settleBargeIn()) {
                    confirmBargeIn(s, true);
                }
                executors.submit(() -> turnRunner.advance(s, text, true));
                return;
            }
            executors.submit(() -> {
                if (isTakingTheFloor(s, text)) {
                    if (s.settleBargeIn()) {
                        confirmBargeIn(s, true);
                    }
                    turnRunner.advance(s, text, true);
                } else {
                    if (s.settleBargeIn()) {
                        metrics.backchannelIgnored();
                        resumeBot(s, "semantic agreement: " + text);
                        log.info("[{}] caller text '{}' evaluated as non-interruption — carrying on", s.channelId(), text);
                    }
                }
            });
            return;
        }

        if (!floorDecided && s.isInterrupted() && s.hasUnspokenText() && !saidEnoughToInterrupt(s, text)) {
            // The bot was silenced at once (HIGH sensitivity) and the caller said only a
            // word or two. Listener sounds skip the model entirely.
            if (FloorRules.isBackchannel(text)) {
                metrics.backchannelIgnored();
                log.info("[{}] caller agreed over the bot ({}) — leaving the cut-off reply to resume", s.channelId(), text);
                return;
            }
            executors.submit(() -> {
                if (isTakingTheFloor(s, text)) {
                    turnRunner.advance(s, text, true);
                    return;
                }
                metrics.backchannelIgnored();
                log.info("[{}] caller agreed over the bot rather than answering it — "
                        + "leaving the cut-off reply to resume", s.channelId());
                log.debug("[{}] backchannel transcript: {}", s.channelId(), text);
            });
            return;
        }
        executors.submit(() -> turnRunner.advance(s, text, true));
    }

    /**
     * Barge-in (PROJECT.md §7.2): the client started speaking.
     *
     * <p>On {@code HIGH} sensitivity the bot is silenced at once (the playback flushed and
     * the turn cancelled). Otherwise it is only <em>paused</em>: listeners say "aha", cough
     * and breathe over a speaker far more often than they take the floor, and the VAD
     * hears all of it as speech. What the caller says decides: enough words confirm the
     * interruption outright ({@link #onInterim}); a word or two is put to the model against
     * the line it was said over ({@link #onFinal}); and {@link #BARGE_IN_CONFIRM_MS} of no
     * words at all resumes the bot exactly where it stopped.
     */
    public boolean onBargeIn(DialogSession s) {
        // Someone is talking, so the line is not silent — even if the recognizer never
        // turns it into a final. Without this the watchdog would prompt over a caller
        // whose speech simply failed to transcribe.
        s.touchActivity();
        InterruptionSensitivity sensitivity = sensitivity(s);
        if (sensitivity == InterruptionSensitivity.OFF) {
            log.debug("[{}] barge-in ignored because agent interruption sensitivity is OFF", s.channelId());
            return false;
        }
        boolean playing = s.endpoint().isPlaying();
        if (!playing && !s.busy().get()) {
            log.debug("[{}] barge-in ignored — the bot owed the caller nothing", s.channelId());
            return false;
        }
        if (sensitivity == InterruptionSensitivity.HIGH) {
            confirmBargeIn(s, playing);
            return true;
        }
        s.endpoint().pausePlayback();
        long token = s.markBargeInPending();
        log.info("[{}] caller speech over the bot — paused, waiting for words", s.channelId());
        executors.scheduleOnWorker(() -> resumeIfNoWords(s, token), BARGE_IN_CONFIRM_MS);
        return true;
    }

    /**
     * The caller pressed a key. Unlike a sound on the line it cannot be a cough or an "aha",
     * so it silences the bot outright instead of pausing it to wait for words — words are
     * never coming, and the pause would resume the bot over a caller still typing. The
     * typed sequence itself arrives later as one final ({@code DialogEngine#onKeypadInput}).
     */
    public void onKeypress(DialogSession s) {
        s.touchActivity();
        s.setTyping(true);
        if (sensitivity(s) == InterruptionSensitivity.OFF) {
            return;
        }
        boolean playing = s.endpoint().isPlaying();
        boolean pending = s.settleBargeIn();
        if ((playing || pending || s.busy().get()) && !s.isCancelled()) {
            confirmBargeIn(s, playing);
        }
    }

    /**
     * A caller interim while the bot is paused on a barge-in: enough words confirm it on
     * the spot. Anything shorter waits for its final, where the model decides.
     */
    public void onInterim(DialogSession s, String text) {
        if (s.isBargeInPending() && saidEnoughToInterrupt(s, text) && s.settleBargeIn()) {
            confirmBargeIn(s, true);
        }
    }

    /**
     * The caller has kept talking over the paused bot for {@code sustained-speech-ms}. A
     * listener's "aha" never lasts that long, so the floor is theirs before a word is
     * transcribed — and in whatever dialect, since no word is matched at all. Their final
     * then arrives with nothing pending and takes the path of a HIGH-sensitivity barge-in:
     * a word or two is still put to the model, which can let the cut-off reply resume.
     */
    public void onSustainedSpeech(DialogSession s) {
        if (s.isBargeInPending() && s.settleBargeIn()) {
            metrics.floorDecidedBySpeech();
            confirmBargeIn(s, true);
        }
    }

    private void confirmBargeIn(DialogSession s, boolean playing) {
        s.endpoint().flushPlayback();
        s.setCancelled(true);
        s.setInterrupted(true);
        // Any reply written ahead was written for a caller who had not interrupted, and
        // the next turn's annex will say they did. Cancel it rather than answer with it.
        s.discardSpeculation();
        metrics.bargeIn();
        s.recordBargeIn();
        log.info("[{}] barge-in: bot silenced ({})", s.channelId(),
                playing ? "mid-utterance" : "before its reply was spoken");
    }

    private void resumeBot(DialogSession s, String why) {
        s.endpoint().resumePlayback();
        log.info("[{}] barge-in not confirmed ({}) — bot carries on", s.channelId(), why);
    }

    /** The pause ran out with no words, or only words that keep it a pause. */
    private void resumeIfNoWords(DialogSession s, long token) {
        if (s.isEnded() || !s.isBargeInPending()) {
            return;
        }
        if (s.heardCallerWithin(BARGE_IN_CONFIRM_MS)) {
            // Words are arriving; their final decides. Look again in case it never comes.
            executors.scheduleOnWorker(() -> resumeIfNoWords(s, token), BARGE_IN_CONFIRM_MS);
            return;
        }
        if (s.settleBargeIn(token)) {
            metrics.falseBargeIn();
            resumeBot(s, "no words");
        }
    }

    /**
     * Whether the caller has said enough over the bot that it can only be them taking the
     * floor — a listener's "aha" is a word or two in any language, never a sentence. LOW
     * sensitivity wants twice as much before it yields.
     *
     * <p>Filler-only utterances ("ha", "aha", "mm") never trigger barge-in outright, even
     * when the word count meets the threshold: they are backchannel, not floor-taking.
     * If the bot asked a question, a single "ha" is an answer — that case is handled by
     * {@link #isTakingTheFloor} via the model, not here.
     */
    private boolean saidEnoughToInterrupt(DialogSession s, String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        // Filler-only text never triggers barge-in — let isTakingTheFloor decide instead.
        if (FloorRules.isBackchannel(text)) {
            return false;
        }
        int words = Backchannels.words(text).length;
        int needed = Math.max(1, dialogProperties.minInterruptionWords());
        if (sensitivity(s) == InterruptionSensitivity.LOW) {
            needed *= 2;
        }
        return words >= needed;
    }

    /**
     * Whether a caller who said a word or two over the bot is taking the floor — by
     * {@link FloorRules} when the words settle it, by {@link FloorLearning} when the model
     * has settled them before, else by the model. A model that gives no ruling yields.
     */
    private boolean isTakingTheFloor(DialogSession s, String text) {
        String botLine = currentBotLine(s);
        Boolean byRule = FloorRules.decide(text);
        if (byRule != null) {
            metrics.floorDecided(true);
            return byRule;
        }
        if (floorLearning.isLearnedTakingTheFloor(text)) {
            metrics.floorDecidedByLearning();
            return true;
        }
        metrics.floorDecided(false);
        long startedAt = System.nanoTime();
        Boolean taking = llmTurnClient.isTakingTheFloor(s, botLine, text);
        metrics.recordFloorModelLatency((System.nanoTime() - startedAt) / 1_000_000);
        if (taking == null) {
            return true;
        }
        executors.submit(() -> floorLearning.recordModelDecision(text, botLine, taking));
        return taking;
    }

    /** What the bot was saying when it was talked over — this turn's, else the last line. */
    private static String currentBotLine(DialogSession s) {
        String heard = s.spokenText();
        return heard != null && !heard.isBlank() ? heard : s.lastAgentText();
    }

    private static InterruptionSensitivity sensitivity(DialogSession s) {
        return s.agent() != null ? s.agent().callBehaviour().interruptionSensitivity() : InterruptionSensitivity.MEDIUM;
    }

    /**
     * Answer the caller in the language they are actually speaking.
     *
     * <p>A campaign dials one list in two languages and the language on the CRM row is
     * often a default nobody checked, so the call opens in the wrong one more often than
     * anyone would like. Two ways out of it, with deliberately different bars:
     *
     * <ul>
     *   <li><b>Asked for</b> ("ruscha gapiring") — not here: the model hears the request
     *       however it is worded and switches on the spot with its switchLanguage tool.
     *   <li><b>Answered in</b> — the detector has to say the same thing on
     *       {@code language-switch-finals} finals in a row. One sentence can be
     *       misrecognized, and switching on it answers an Uzbek caller in Russian.
     * </ul>
     *
     * <p>Switching changes the voice and drops the cached prompt prefix
     * ({@link DialogSession#switchLanguage}), which is why it is not done lightly — but a
     * caller being answered in a language they do not speak ends the call either way.
     */
    private void adaptLanguage(DialogSession s, String text) {
        List<String> candidates = !s.detectLanguages().isEmpty() ? s.detectLanguages() : sttProperties.detectLanguages();
        if (candidates == null || candidates.size() < 2) {
            return; // a single-language deployment has nothing to switch to
        }
        String spoken = languageDetector.detect(text, candidates);
        if (spoken == null) {
            return; // too short, or a word both languages share
        }
        if (spoken.equalsIgnoreCase(s.language())) {
            s.resetLanguageCandidate();
            return;
        }
        int finals = s.noteLanguageCandidate(spoken);
        if (finals >= dialogProperties.languageSwitchFinals()) {
            log.info("[{}] caller has answered in {} {} times running — switching from {}",
                    s.channelId(), spoken, finals, s.language());
            s.switchLanguage(spoken);
        }
    }

    /**
     * Checks if the given transcript contains one of the given machine/carrier phrases.
     */
    private boolean containsAny(String text, List<String> phrases) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String phrase : phrases) {
            if (normalized.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a caller transcript is really the bot's own audio coming back.
     */
    private boolean isEchoOfBot(DialogSession s, String text) {
        String caller = TranscriptText.normalize(text);
        return caller.length() >= dialogTuning.minEchoChars() && TranscriptText.normalize(s.lastAgentText()).contains(caller);
    }
}
