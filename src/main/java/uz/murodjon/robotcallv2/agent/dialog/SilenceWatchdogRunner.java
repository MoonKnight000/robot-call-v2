package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.agent.metrics.VoiceMetrics;
import uz.murodjon.robotcallv2.shared.dialog.DialogPhrases;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

/**
 * Watches a call for a caller who has stopped answering (PROJECT.md §7.3): asks once or
 * twice whether they are still there, then says goodbye and hangs up.
 *
 * <p>A dead line costs the same as a conversation, and the usual cause is a caller who
 * put the phone down without hanging up. {@link NoInputWatchdog} holds the counting; this
 * drives it and carries out what it decides.
 *
 * <p>The tick runs on the shared scheduler thread, so it only ever decides — both the
 * prompt (a TTS round trip) and the close (which waits for audio to drain) are handed to
 * the worker.
 */
@Component
public class SilenceWatchdogRunner {

    private static final Logger log = LoggerFactory.getLogger(SilenceWatchdogRunner.class);

    /**
     * How often the silence watchdog looks at a call. Finer than the idle threshold it
     * enforces, so the prompt lands close to the moment the threshold is crossed rather
     * than up to a whole threshold late.
     */
    private static final Duration WATCHDOG_TICK = Duration.ofSeconds(1);

    private final DialogProperties props;
    private final VoiceMetrics metrics;
    private final DialogTranscript transcript;
    private final SpeechOutput speech;
    private final DialogExecutors executors;

    public SilenceWatchdogRunner(DialogProperties props, VoiceMetrics metrics, DialogTranscript transcript,
                                 SpeechOutput speech, DialogExecutors executors) {
        this.props = props;
        this.metrics = metrics;
        this.transcript = transcript;
        this.speech = speech;
        this.executors = executors;
    }

    /** A silence watchdog for a new call, or {@code null} when it is switched off. */
    public NoInputWatchdog createWatchdog() {
        if (props.noInputSeconds() <= 0) {
            return null;
        }
        return new NoInputWatchdog(Duration.ofSeconds(props.noInputSeconds()),
                props.noInputMaxPrompts(), Instant.now());
    }

    /** Begin ticking for {@code s}; the handle is kept on the session so teardown can cancel it. */
    public void start(DialogSession s) {
        if (s.watchdog() == null) {
            return;
        }
        ScheduledFuture<?> task = executors.scheduleTicks(() -> tick(s), WATCHDOG_TICK);
        s.setWatchdogTask(task);
    }

    /**
     * One watchdog tick. Runs on the scheduler thread, so it only decides — the prompt
     * (TTS) and the close (which waits for audio to drain) go to the worker.
     */
    private void tick(DialogSession s) {
        try {
            if (s.isEnded()) {
                return;
            }
            NoInputAction action = s.watchdog()
                    .check(Instant.now(), s.endpoint().isPlaying(), s.busy().get());
            switch (action) {
                case NONE -> {
                }
                case PROMPT -> executors.submit(() -> promptForInput(s));
                case END -> executors.submit(() -> closeOnSilence(s));
            }
        } catch (Exception e) {
            log.warn("[{}] watchdog tick failed: {}", s.channelId(), e.getMessage());
        }
    }

    /**
     * Ask whether the caller is still there, and put the unanswered question to them
     * again. Spoken from code rather than through the model: there is no new client input
     * to answer, so a turn would only ask the LLM to invent something, and the point is
     * to break the silence quickly.
     *
     * <p>"Alo, eshityapsizmi?" on its own leaves the caller having to remember what they
     * were asked — the question came a whole idle threshold ago, and the usual reason for
     * the silence is that they did not catch it. Repeating it verbatim is usually free too:
     * a reply is streamed sentence by sentence, so the question was normally synthesized
     * on its own and comes straight back out of the TTS cache.
     */
    private void promptForInput(DialogSession s) {
        if (s.isEnded() || !s.busy().compareAndSet(false, true)) {
            return; // a real turn started in the meantime — it will speak anyway
        }
        try {
            MDC.put("channelId", s.channelId());
            // A barge-in that was never followed by speech leaves the cancel flag set,
            // and that flag would silently swallow this prompt — the one moment the
            // caller most needs to hear something.
            s.setCancelled(false);
            String line = DialogPhrases.stillThere(s.language());
            String question = DialogLines.lastQuestion(s.lastAgentText());
            log.info("[{}] no input for {}s — prompting ({}/{}){}", s.channelId(),
                    props.noInputSeconds(), s.watchdog().prompts(), props.noInputMaxPrompts(),
                    question != null ? ", repeating the question" : "");
            boolean spoken = speech.speakChunk(s, line);
            // Two chunks rather than one string: each is a cache key of its own, and both
            // have been spoken before.
            if (question != null && speech.speakChunk(s, question)) {
                line = line + " " + question;
                spoken = true;
            }
            if (spoken) {
                // Recorded in the history so the model can see it asked, and in the
                // transcript so the summary reflects a caller who went quiet.
                s.history().add(new AssistantMessage(line));
                s.setLastAgentText(line);
                transcript.recordAgentLine(s, line);
            }
            metrics.noInputPrompt();
        } finally {
            s.busy().set(false);
            MDC.remove("channelId");
        }
    }

    /** Give up on a call nobody is speaking on: say goodbye and hang up. */
    private void closeOnSilence(DialogSession s) {
        if (s.isEnded()) {
            return;
        }
        log.info("[{}] closing dialog (no input after {} prompt(s))",
                s.channelId(), s.watchdog().prompts());
        // HUNG_UP rather than NO_ANSWER: the call was answered, so the number works and
        // the target is worth retrying — but nothing was agreed.
        s.end(Disposition.HUNG_UP);
        metrics.noInputHangup();
        if (!s.busy().compareAndSet(false, true)) {
            // A turn claimed the session between the check and here. It sees isEnded()
            // and finishes the call itself once it has spoken.
            return;
        }
        try {
            MDC.put("channelId", s.channelId());
            s.setCancelled(false);
            speech.speakChunk(s, DialogLines.farewell(s));
            speech.finishWhenSpoken(s);
        } finally {
            s.busy().set(false);
            MDC.remove("channelId");
        }
    }
}
