package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.metrics.VoiceMetrics;

import java.util.List;

/**
 * What happens after a barge-in: deciding after the fact whether anybody actually spoke,
 * resuming the reply that was cut off when nobody did, and folding a cut-off caller
 * utterance into the one that completes it.
 */
@Component
class BargeInRecovery {

    private static final Logger log = LoggerFactory.getLogger(BargeInRecovery.class);

    private final DialogProperties dialogProperties;
    private final DialogExecutors executors;
    private final VoiceMetrics metrics;
    private final SpeechOutput speech;
    private final DialogTranscript transcript;

    BargeInRecovery(DialogProperties dialogProperties, DialogExecutors executors, VoiceMetrics metrics,
                    SpeechOutput speech, DialogTranscript transcript) {
        this.dialogProperties = dialogProperties;
        this.executors = executors;
        this.metrics = metrics;
        this.speech = speech;
        this.transcript = transcript;
    }

    /**
     * Arm the check that decides, after the fact, whether a barge-in was a real one.
     *
     * <p>A VAD fires on a cough, a chair, a voice in the room and the bot's own audio
     * coming back off a speakerphone, exactly as it fires on the caller — and by the time
     * anything can tell the difference the bot has already been silenced, which is the
     * right order (waiting to be sure means talking over someone who really did speak).
     * What separates the two afterwards is simple: a real interruption is followed by
     * words, and words start a turn. So if the turn number has not moved by the time this
     * runs, nobody spoke, and the caller is sitting in a silence the bot created.
     */
    void scheduleFalseInterruptionCheck(DialogSession s, int turn, Runnable onFalseInterruption) {
        if (dialogProperties.falseInterruptionTimeoutMs() <= 0) {
            return;
        }
        executors.scheduleOnWorker(() -> runIfFalseInterruption(s, turn, onFalseInterruption),
                dialogProperties.falseInterruptionTimeoutMs());
    }

    /**
     * Silenced, but nothing came back to show for it. Give the words a moment to arrive
     * before deciding the interruption was real — and resume the reply if it was not.
     */
    void scheduleResume(DialogSession s, int turn) {
        scheduleFalseInterruptionCheck(s, turn, () -> resumeInterruptedReply(s, turn));
    }

    /**
     * Fold the previous, cut-off caller utterance into this one, when there is one.
     *
     * <p>The previous final was the first half of this sentence: the gate closed on a
     * pause, the caller carried on, and the reply to the half was cancelled before a word
     * of it went out. Answer the whole thought once, not its halves twice — and the model
     * was not interrupted, because the caller never heard it start.
     *
     * @return the text this turn answers: the stitched sentence, or {@code clientText} as it was
     */
    String stitchUnansweredUtterance(DialogSession s, String clientText) {
        String unanswered = s.takeUnansweredClientText();
        if (unanswered == null) {
            return clientText;
        }
        dropUnansweredMessage(s, unanswered);
        String stitched = unanswered + " " + clientText;
        s.setInterrupted(false);
        metrics.turnStitched();
        // The caller's words are personal data — DEBUG, like the other transcript lines.
        log.debug("[{}] stitched the cut-off utterance onto this one: {}", s.channelId(), stitched);
        return stitched;
    }

    /** Carry out {@code onFalseInterruption} if the call is still where the barge-in left it. */
    private void runIfFalseInterruption(DialogSession s, int turn, Runnable onFalseInterruption) {
        if (s.isEnded() || s.turnCount() != turn || s.busy().get() || s.endpoint().isPlaying()) {
            return; // the caller really was speaking, or something else is talking to them
        }
        if (s.heardCallerWithin(dialogProperties.falseInterruptionTimeoutMs())) {
            // Interims are still arriving: the caller is mid-sentence and the final that
            // ends it has simply not come yet. Resuming the reply now would talk over them —
            // which is the cut-off this check exists to avoid. Ask again later.
            log.debug("[{}] barge-in check deferred — the caller is still speaking", s.channelId());
            scheduleFalseInterruptionCheck(s, turn, onFalseInterruption);
            return;
        }
        metrics.falseBargeIn();
        log.info("[{}] no speech followed the barge-in after {} ms — treating it as noise",
                s.channelId(), dialogProperties.falseInterruptionTimeoutMs());
        // The model was never actually cut off, so the next turn must not be told it was.
        s.setInterrupted(false);
        s.setCancelled(false);
        onFalseInterruption.run();
    }

    /**
     * Speak the tail of a reply the barge-in stopped, once it has turned out that nobody
     * was interrupting.
     *
     * <p>Resuming rather than re-asking is what a person does: the sentence was half out,
     * the noise was not an answer, and starting over ("uzr, takrorlang") makes the caller
     * repeat something they never said. It is also what the mature voice stacks default
     * to. Only the unspoken remainder is played — the caller already heard the rest.
     */
    private void resumeInterruptedReply(DialogSession s, int turn) {
        if (!s.busy().compareAndSet(false, true)) {
            return; // a real turn claimed the call in the meantime; it will speak
        }
        try {
            MDC.put("channelId", s.channelId());
            String rest = s.takeUnspokenText();
            if (rest == null || s.isEnded() || s.turnCount() != turn) {
                return;
            }
            if (!speech.speakChunk(s, rest)) {
                return;
            }
            s.setUnansweredClientText(null); // the caller has their answer after all
            String merged = mergeResumedReply(s, rest);
            s.setLastAgentText(merged);
            transcript.recordAgentLine(s, rest);
            log.debug("[{}] AGENT ({}, resumed after a false barge-in): {}",
                    s.channelId(), s.state(), rest);
        } finally {
            s.busy().set(false);
            MDC.remove("channelId");
        }
    }

    /**
     * Remove the half-sentence user message the cancelled turn left in the history, so the
     * stitched sentence replaces it rather than following it. Searched from the end: a
     * tool-rejection note may sit after it.
     */
    private static void dropUnansweredMessage(DialogSession s, String unanswered) {
        List<Message> history = s.history();
        for (int i = history.size() - 1; i >= 0 && i >= history.size() - 2; i--) {
            if (history.get(i) instanceof UserMessage user && unanswered.equals(user.getText())) {
                history.remove(i);
                return;
            }
        }
    }

    /**
     * Fold a resumed tail back into the reply it belongs to, so the history holds the one
     * line the model wrote rather than two consecutive assistant messages describing half
     * a sentence each.
     *
     * @return the whole line as the caller ended up hearing it
     */
    private static String mergeResumedReply(DialogSession s, String rest) {
        List<Message> history = s.history();
        int last = history.size() - 1;
        if (last >= 0 && history.get(last) instanceof AssistantMessage spoken
                && spoken.getText() != null && !spoken.getText().isBlank()) {
            String merged = spoken.getText().trim() + " " + rest;
            history.set(last, new AssistantMessage(merged));
            return merged;
        }
        history.add(new AssistantMessage(rest));
        return rest;
    }
}
