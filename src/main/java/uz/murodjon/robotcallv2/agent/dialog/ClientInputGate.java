package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.metrics.VoiceMetrics;
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
     * Shortest caller transcript that may be dismissed as the bot's own echo. Long enough
     * that a caller reading a figure back, or agreeing in the bot's own words, is never
     * thrown away — only a verbatim stretch of the agent's last line reaches it.
     */
    private static final int MIN_ECHO_CHARS = 20;

    /**
     * Finals in a row in the other language before the call turns over to it. Two, because
     * one is a misrecognition and three is a caller who has already given up.
     */
    private static final int LANGUAGE_SWITCH_FINALS = 2;

    /**
     * Common Uzbek and Russian carrier voicemail/answering machine phrases.
     */
    private static final List<String> VOICEMAIL_PHRASES = List.of(
            "apparati o'chirilgan", "xizmat doirasidan tashqarida", "ovozli xabar",
            "ovozli xabar qoldiring", "signal ovozidan so'ng", "signal ovozidan keyin",
            "telefon o'chirilgan", "boshqa raqamga yo'naltirilgan", "chaqirilayotgan abonent",
            "vaqtincha javob bermayapti", "iltimos, keyinroq qo'ng'iroq qiling",
            "keyinroq qo'ng'iroq qiling", "javob bermayapti",
            "абонент недоступен", "аппарат абонента выключен", "находится вне зоны",
            "оставьте сообщение", "после сигнала", "после звукового сигнала",
            "автоответчик", "перезвоните позже", "линия занята", "номер не существует",
            "вызываемый абонент занят", "данный вид связи недоступен", "номер временно заблокирован",
            "not in service", "currently unavailable", "switched off"
    );

    private final DialogProperties dialogProperties;
    private final SttProperties sttProperties;
    private final LanguageDetector languageDetector;
    private final VoiceMetrics metrics;
    private final DialogTranscript transcript;
    private final TurnRunner turnRunner;
    private final DialogExecutors executors;

    public ClientInputGate(DialogProperties dialogProperties, SttProperties sttProperties, LanguageDetector languageDetector,
                           VoiceMetrics metrics, DialogTranscript transcript,
                           TurnRunner turnRunner, DialogExecutors executors) {
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

        // Check for Voicemail / Answering machine transcript in early turns (turn <= 2)
        if (s.turnCount() <= 2 && isVoicemailTranscript(text)) {
            log.info("[{}] Voicemail phrase detected in client transcript: '{}'", s.channelId(), text);
            s.setDisposition(Disposition.VOICEMAIL);
            s.end(Disposition.VOICEMAIL);

            AiAgent agent = s.agent();
            // HANGUP only when there is no agent at all: it has no message to leave.
            VoicemailAction action = (agent != null)
                    ? agent.callBehaviour().voicemailAction()
                    : VoicemailAction.HANGUP;

            if (action == VoicemailAction.IGNORE) {
                log.info("[{}] Agent voicemailAction is IGNORE - continuing call despite voicemail phrase", s.channelId());
            } else if (action == VoicemailAction.LEAVE_MESSAGE && agent.callBehaviour().voicemailMessage() != null && !agent.callBehaviour().voicemailMessage().isBlank()) {
                log.info("[{}] Agent voicemailAction is LEAVE_MESSAGE - speaking voicemail message before hangup", s.channelId());
                executors.submit(() -> turnRunner.leaveVoicemailAndClose(s, agent.callBehaviour().voicemailMessage()));
                return;
            } else {
                if (s.hangup() != null) {
                    s.hangup().run();
                }
                return;
            }
        }

        adaptLanguage(s, text);

        if (s.disposition() != null && Farewells.saidByCaller(text)) {
            // The outcome is already on the record and the caller is closing, not asking.
            // A turn here would open with "Bir lahza" over a two-syllable
            // "rahmat" and end with a goodbye the caller has already been given.
            log.info("[{}] caller signed off ({}) — answering with a goodbye, not a turn",
                    s.channelId(), text);
            executors.submit(() -> turnRunner.closeOnCallerFarewell(s));
            return;
        }

        if (isBackchannelOverTheBot(s, text)) {
            metrics.backchannelIgnored();
            log.info("[{}] caller agreed over the bot rather than answering it ({}) — "
                    + "leaving the cut-off reply to resume", s.channelId(), text);
            // Deliberately not resumed from here. The barge-in that silenced the bot armed
            // TurnRunner's false-interruption check already, and not starting a turn is
            // exactly what lets it find the call where it left it and speak the rest.
            return;
        }
        executors.submit(() -> turnRunner.advance(s, text, true));
    }

    /**
     * Barge-in (PROJECT.md §7.2): the client started speaking. Silence the bot at once
     * (flush the RTP playback) and cancel the turn still producing the reply, so the
     * sentences the client talked over are never synthesized.
     */
    public boolean onBargeIn(DialogSession s) {
        // Someone is talking, so the line is not silent — even if the recognizer never
        // turns it into a final. Without this the watchdog would prompt over a caller
        // whose speech simply failed to transcribe.
        s.touchActivity();
        if (s.agent() != null && s.agent().callBehaviour().interruptionSensitivity() == InterruptionSensitivity.OFF) {
            log.debug("[{}] barge-in ignored because agent interruption sensitivity is OFF", s.channelId());
            return false;
        }
        boolean playing = s.endpoint().isPlaying();
        if (!playing && !s.busy().get()) {
            log.debug("[{}] barge-in ignored — the bot owed the caller nothing", s.channelId());
            return false;
        }
        s.endpoint().flushPlayback();
        s.setCancelled(true);
        s.setInterrupted(true);
        // Any reply written ahead was written for a caller who had not interrupted, and
        // the next turn's annex will say they did. Cancel it rather than answer with it.
        s.discardSpeculation();
        metrics.bargeIn();
        log.info("[{}] barge-in: bot silenced ({})", s.channelId(),
                playing ? "mid-utterance" : "before its reply was spoken");
        return true;
    }

    /**
     * Answer the caller in the language they are actually speaking.
     *
     * <p>A campaign dials one list in two languages and the language on the CRM row is
     * often a default nobody checked, so the call opens in the wrong one more often than
     * anyone would like. Two ways out of it, with deliberately different bars:
     *
     * <ul>
     *   <li><b>Asked for</b> ("ruscha gapiring") — switched on the spot. The caller said
     *       what they want; making them say it twice is worse than a misrecognition.
     *   <li><b>Answered in</b> — the detector has to say the same thing on
     *       {@link #LANGUAGE_SWITCH_FINALS} finals in a row. One sentence can be
     *       misrecognized, and switching on it answers an Uzbek caller in Russian.
     * </ul>
     *
     * <p>Switching changes the voice and drops the cached prompt prefix
     * ({@link DialogSession#switchLanguage}), which is why it is not done lightly — but a
     * caller being answered in a language they do not speak ends the call either way.
     */
    private void adaptLanguage(DialogSession s, String text) {
        List<String> candidates = sttProperties.detectLanguages();
        if (candidates == null || candidates.size() < 2) {
            return; // a single-language deployment has nothing to switch to
        }
        String requested = DialogLanguageSwitcher.detectLanguageSwitch(text, s.language()).orElse(null);
        if (requested != null) {
            log.info("[{}] caller asked to be spoken to in {} — switching from {}",
                    s.channelId(), requested, s.language());
            s.switchLanguage(requested);
            return;
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
        if (finals >= LANGUAGE_SWITCH_FINALS) {
            log.info("[{}] caller has answered in {} {} times running — switching from {}",
                    s.channelId(), spoken, finals, s.language());
            s.switchLanguage(spoken);
        }
    }

    /**
     * Checks if the given transcript matches known voicemail/answering machine patterns.
     */
    private boolean isVoicemailTranscript(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String phrase : VOICEMAIL_PHRASES) {
            if (normalized.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a caller transcript is agreement over the top of the bot rather than an
     * answer to it (see {@link Backchannels}).
     */
    private boolean isBackchannelOverTheBot(DialogSession s, String text) {
        return s.isInterrupted()
                && s.hasUnspokenText()
                && Backchannels.matches(text, dialogProperties.minInterruptionWords());
    }

    /**
     * Whether a caller transcript is really the bot's own audio coming back.
     */
    private static boolean isEchoOfBot(DialogSession s, String text) {
        String caller = TranscriptText.normalize(text);
        return caller.length() >= MIN_ECHO_CHARS && TranscriptText.normalize(s.lastAgentText()).contains(caller);
    }
}
