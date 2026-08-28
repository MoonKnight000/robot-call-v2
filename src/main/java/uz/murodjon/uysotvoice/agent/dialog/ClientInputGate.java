package uz.murodjon.uysotvoice.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.agent.metrics.VoiceMetrics;
import uz.murodjon.uysotvoice.agent.rtp.RtpEndpoint;

/**
 * What the recognizer hands over, and whether it is worth a turn (PROJECT.md §7.2).
 * The first box after the STT stream: everything here decides between the caller's
 * words and the LLM, and nothing here talks to the model itself.
 *
 * <p>Two things a recognizer produces are not the caller answering, and both used to
 * start turns:
 *
 * <ul>
 *   <li><b>The bot's own voice</b>, transcribed off a speakerphone with no echo
 *       cancellation — answering it starts a loop the caller cannot get a word into.
 *   <li><b>Agreement over the top of the bot</b> ("ha", "aha") while a cut-off reply is
 *       still waiting to be finished — answering it abandons the sentence the caller
 *       was agreeing with.
 * </ul>
 *
 * <p>Barge-in also lands here, because it is the same question asked of the audio rather
 * than of the transcript: is the caller taking the floor, and does the bot owe them
 * silence.
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

    private final DialogProperties props;
    private final VoiceMetrics metrics;
    private final DialogTranscript transcript;
    private final TurnRunner turnRunner;
    private final DialogExecutors executors;

    public ClientInputGate(DialogProperties props, VoiceMetrics metrics, DialogTranscript transcript,
                           TurnRunner turnRunner, DialogExecutors executors) {
        this.props = props;
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
     *
     * <p>"Mid-utterance" is deliberately wider than "audio is on the wire right now".
     * {@link RtpEndpoint#isPlaying()} is false in every gap between two streamed
     * sentences — the next one is still being synthesized — and for the whole second or
     * two a turn spends inside the LLM before any audio exists. Those gaps are precisely
     * when a person interrupts: they wait for the end of a sentence and then speak. While
     * this only looked at {@code isPlaying()}, such a barge-in set nothing at all, the
     * turn carried on synthesizing, and the caller heard the bot answer a question they
     * had already talked over — the "I said stop and it kept going" complaint.
     *
     * @return whether there was in fact a reply to interrupt; the detector uses this to
     *         decide whether it has spent its arming (see {@code VadStream})
     */
    public boolean onBargeIn(DialogSession s) {
        // Someone is talking, so the line is not silent — even if the recognizer never
        // turns it into a final. Without this the watchdog would prompt over a caller
        // whose speech simply failed to transcribe.
        s.touchActivity();
        boolean playing = s.endpoint().isPlaying();
        if (!playing && !s.busy().get()) {
            log.debug("[{}] barge-in ignored — the bot owed the caller nothing", s.channelId());
            return false;
        }
        if (playing) {
            s.endpoint().flushPlayback();
        }
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
     * Whether a caller transcript is agreement over the top of the bot rather than an
     * answer to it (see {@link Backchannels}).
     *
     * <p>Scoped to a reply that was cut off part-way and still has a tail waiting: only
     * there is ignoring the words the better of the two options, because only there is
     * there something for the bot to go back to. A barge-in over already-queued audio
     * leaves no text to resume — that playback is flushed and gone — so an "aha" there is
     * answered, silence being worse than a non-sequitur.
     */
    private boolean isBackchannelOverTheBot(DialogSession s, String text) {
        return s.isInterrupted()
                && s.hasUnspokenText()
                && Backchannels.matches(text, props.minInterruptionWords());
    }

    /**
     * Whether a caller transcript is really the bot's own audio coming back.
     *
     * <p>On a speakerphone the handset's microphone hears its own earpiece, and the
     * carrier has no acoustic echo cancellation to remove it — so the recognizer
     * transcribes what the bot just said and attributes it to the caller. Twenty-odd
     * characters of the agent's last line reproduced verbatim is not a coincidence; a
     * caller confirming something back ("bir yarim million, to'g'rimi?") is far shorter
     * than that and rarely a literal substring.
     *
     * <p>Only a symptom check. The real fix belongs on the Asterisk side; this stops the
     * conversation looping while that is missing, and {@code voice.dialog.echo.suppressed}
     * is what says it is happening.
     */
    private static boolean isEchoOfBot(DialogSession s, String text) {
        String caller = TranscriptText.normalize(text);
        return caller.length() >= MIN_ECHO_CHARS && TranscriptText.normalize(s.lastAgentText()).contains(caller);
    }
}
