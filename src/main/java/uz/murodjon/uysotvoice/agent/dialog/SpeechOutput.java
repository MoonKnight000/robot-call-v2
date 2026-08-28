package uz.murodjon.uysotvoice.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.agent.metrics.VoiceMetrics;
import uz.murodjon.uysotvoice.agent.tts.PcmChunkListener;
import uz.murodjon.uysotvoice.agent.tts.TtsProperties;
import uz.murodjon.uysotvoice.agent.tts.TtsRouter;
import uz.murodjon.uysotvoice.shared.dialog.DialogPhrases;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The speech end of the pipeline: everything between a line of text and the caller
 * hearing it (PROJECT.md §2.5, §7.2). Synthesis itself belongs to {@link TtsRouter} and
 * the providers behind it; what is here is the part that depends on the call — whether
 * the line may be spoken at all, whether it is still wanted by the time the audio
 * exists, and what the caller hears while it is being made.
 *
 * <p>Three things live here rather than in the turn that asks for the speech, because
 * all three are decided against the state of the audio and not the state of the
 * conversation:
 *
 * <ul>
 *   <li><b>The fact guard</b> (§4.4) — the last check before a figure reaches the wire.
 *       It is here because this is the narrowest point every spoken line passes through.
 *   <li><b>Cancellation</b> — a barge-in that lands mid-synthesis must cost the caller
 *       nothing, so the flag is re-read on both sides of every round trip.
 *   <li><b>The §1.3 turnaround clock</b> — it stops on the first PCM chunk that reaches
 *       the endpoint, which is the only place that moment is known.
 * </ul>
 */
@Component
public class SpeechOutput {

    private static final Logger log = LoggerFactory.getLogger(SpeechOutput.class);

    /** Cap on waiting for queued audio to drain before hanging up anyway. */
    private static final Duration MAX_DRAIN = Duration.ofSeconds(30);

    private final DialogProperties props;
    private final TtsProperties ttsProps;
    private final TtsRouter ttsRouter;
    private final VoiceMetrics metrics;
    private final DialogExecutors executors;

    public SpeechOutput(DialogProperties props, TtsProperties ttsProps, TtsRouter ttsRouter,
                        VoiceMetrics metrics, DialogExecutors executors) {
        this.props = props;
        this.ttsProps = ttsProps;
        this.ttsRouter = ttsRouter;
        this.metrics = metrics;
        this.executors = executors;
    }

    /**
     * Synthesize one piece of the reply and queue it behind whatever is already playing.
     * Called once per sentence while streaming, or once for the whole reply otherwise.
     * Failures are logged, never thrown: a lost sentence is better than an aborted turn.
     */
    public SpeechOutcome speak(DialogSession s, String text) {
        if (!ttsProps.enabled() || text == null || text.isBlank()) {
            return SpeechOutcome.SKIPPED;
        }
        if (s.isCancelled()) {
            // Checked before synthesis, not after: the caller is talking, and paying a TTS
            // round trip for audio that is discarded on arrival delays whatever the next
            // turn wants to say by exactly that round trip.
            return SpeechOutcome.SKIPPED;
        }
        if (props.factGuard()) {
            List<String> bad = FactGuard.violations(text, s.scenario(), s.context());
            if (!bad.isEmpty()) {
                // Not spoken, not recorded as said: the caller must never hear a sum
                // that is not in their file (§4.4). The caller-facing recovery is the
                // turn's, not this method's — see TurnRunner.recoverFromFactBlock.
                s.recordFactViolation();
                metrics.factGuardBlock();
                log.error("[{}] fact guard BLOCKED a sentence in {}: figures {} are not in the "
                                + "call facts ({}) — text was: {}",
                        s.channelId(), s.state(), bad,
                        s.context() != null ? s.context().facts() : null, text);
                return SpeechOutcome.BLOCKED;
            }
        }
        try {
            // Only the turn's first sentence is still on the §1.3 turnaround clock — that
            // is the one place shaving a TTS network round trip actually moves the number
            // that matters (everything after it already overlaps LLM generation, §7.2).
            if (s.isFirstAudioPending()) {
                return speakStreaming(s, text);
            }
            short[] pcm = ttsRouter.synthesize(text, s.language(), s.ttsVoice(), s.voiceSettings());
            if (s.isCancelled()) {
                return SpeechOutcome.SKIPPED; // barge-in landed while we were synthesizing
            }
            s.endpoint().enqueuePcm(pcm);
            s.appendSpokenText(text);
            return SpeechOutcome.SPOKEN;
        } catch (Exception e) {
            log.warn("TTS failed during dialog [{}]: {}", s.channelId(), e.getMessage());
            return SpeechOutcome.SKIPPED;
        }
    }

    /** {@link #speak} for the fixed lines, where only "did the caller hear it" matters. */
    public boolean speakChunk(DialogSession s, String text) {
        return speak(s, text) == SpeechOutcome.SPOKEN;
    }

    /**
     * Chunk-streamed synthesis for the turn's first sentence: each PCM chunk is queued to
     * the endpoint as Yandex's gRPC stream delivers it, instead of waiting for the whole
     * sentence (TtsRouter.synthesizeStreaming). A cache hit or a Google-routed sentence
     * still arrives as one chunk (TtsProvider's default), so this path is never worse than
     * the blocking one — only potentially faster.
     */
    private SpeechOutcome speakStreaming(DialogSession s, String text) {
        AtomicBoolean any = new AtomicBoolean(false);
        PcmChunkListener onChunk = pcm -> {
            if (s.isCancelled()) {
                return; // barge-in landed mid-stream; drain without queuing more audio
            }
            if (any.compareAndSet(false, true)) {
                // Before the enqueue, not after: this also clears isFirstAudioPending(),
                // which is what tells a filler still in flight on another thread that the
                // gap is closed. Doing it afterwards left a window where the filler could
                // re-check, see silence, and queue "bir soniya" behind the real opening.
                recordTurnaround(s); // first chunk of this turn's first sentence
            }
            s.endpoint().enqueuePcm(pcm);
        };
        try {
            ttsRouter.synthesizeStreaming(text, s.language(), s.ttsVoice(), s.voiceSettings(), onChunk);
        } catch (Exception e) {
            log.warn("TTS streaming failed during dialog [{}]: {}", s.channelId(), e.getMessage());
        }
        // Whatever reached the endpoint before a mid-stream barge-in still counts as
        // spoken, same as any other sentence already queued when cancellation lands.
        if (!any.get()) {
            return SpeechOutcome.SKIPPED;
        }
        if (!s.isCancelled()) {
            s.appendSpokenText(text);
        }
        // A cancellation that landed partway through the chunk stream leaves the sentence
        // unrecorded on purpose. Where it was cut is inside the provider's stream and not
        // knowable here, and of the two possible errors only one is cheap: a sentence
        // recorded as unheard is simply said again, while one recorded as heard is a fact
        // — a sum, a due date — the model now believes it delivered and never returns to.
        return SpeechOutcome.SPOKEN;
    }

    private void recordTurnaround(DialogSession s) {
        Duration turnaround = s.takeTurnaround();
        if (turnaround != null) {
            metrics.recordTurnaround(turnaround);
            s.recordTurnLatency(turnaround.toMillis());
            log.debug("[{}] turnaround {} ms", s.channelId(), turnaround.toMillis());
        }
    }

    /**
     * Arm the "bir soniya" filler for a turn that is about to call the LLM, or return
     * {@code null} when this turn should not get one.
     *
     * <p>The filler does not make the reply arrive any sooner — it is queued <em>ahead</em>
     * of it, so strictly it delays the content slightly. What it removes is the silence,
     * and silence is what a caller reacts to: a second and a half of nothing on a phone
     * line reads as a dropped call, and people say "alo?" into it or hang up. A person
     * who needs a moment says so out loud.
     *
     * <p>Deliberately not free of charge to the clock, so it is rationed hard: only after
     * {@code filler-delay-ms} of actual silence, only on a turn a caller is waiting on
     * (never the greeting — {@link DialogSession#isFirstAudioPending()} is false there),
     * and never twice in a row.
     */
    public ScheduledFuture<?> scheduleFiller(DialogSession s) {
        int turn = s.turnCount();
        if (props.fillerDelayMs() <= 0 || !ttsProps.enabled()
                || !s.isFirstAudioPending() || !s.fillerAllowed(turn)) {
            return null;
        }
        return executors.scheduleOnWorker(() -> speakFiller(s, turn), props.fillerDelayMs());
    }

    /**
     * Play one short filler, if the turn is still silent by the time we get here.
     *
     * <p>Bypasses {@link #speak} on purpose. The fact guard has nothing to check (these
     * are fixed lines with no figures in them), and — more importantly — the turnaround
     * clock must not be stopped here. {@code voice.turnaround.latency} is meant to say
     * how long the caller waited for a real answer, and a filler that "recorded" it would
     * turn the §1.3 metric into a measure of how fast we can say "bir soniya".
     * {@code voice.dialog.filler.played} is where this shows up instead.
     *
     * <p>The silence check is made twice: once on entry, and again after synthesis, since
     * a cache miss puts a network round trip in between and the real reply may have
     * started in that window. Queuing it then would put "bir soniya" in the middle of the
     * answer.
     */
    private void speakFiller(DialogSession s, int turn) {
        if (!fillerStillWanted(s)) {
            return;
        }
        try {
            List<String> options = DialogPhrases.thinking(s.language());
            String line = options.get(Math.floorMod(turn, options.size()));
            short[] pcm = ttsRouter.synthesize(line, s.language(), s.ttsVoice(), s.voiceSettings());
            if (!fillerStillWanted(s)) {
                return;
            }
            s.endpoint().enqueuePcm(pcm);
            s.markFillerSpoken(turn);
            metrics.fillerPlayed();
            log.debug("[{}] filler played after {} ms of silence: {}",
                    s.channelId(), props.fillerDelayMs(), line);
        } catch (Exception e) {
            // A filler is a comfort, never a requirement — losing one costs nothing.
            log.debug("[{}] filler skipped: {}", s.channelId(), e.getMessage());
        }
    }

    /** Whether the caller is still sitting in silence and would benefit from a filler. */
    private static boolean fillerStillWanted(DialogSession s) {
        return !s.isEnded() && !s.isCancelled() && s.isFirstAudioPending();
    }

    /**
     * Waits for the queued audio to drain, then ends the call: transfer to a human
     * operator if the outcome is TRANSFERRED (§11.6), otherwise hang up.
     *
     * <p>Polls the endpoint rather than sleeping for a precomputed duration: with
     * sentence streaming the total length is not known when the last sentence is
     * queued, and hanging up early would cut off the goodbye.
     */
    public void finishWhenSpoken(DialogSession s) {
        long deadline = System.nanoTime() + MAX_DRAIN.toNanos();
        try {
            while (s.endpoint().isPlaying() && System.nanoTime() < deadline) {
                Thread.sleep(100);
            }
            Thread.sleep(500); // let the tail reach the caller before the channel drops
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Runnable action = (s.disposition() == Disposition.TRANSFERRED && s.transfer() != null)
                ? s.transfer() : s.hangup();
        if (action != null) {
            action.run();
        }
    }
}
