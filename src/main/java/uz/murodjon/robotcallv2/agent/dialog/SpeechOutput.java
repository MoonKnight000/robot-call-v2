package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.agent.metrics.VoiceMetrics;
import uz.murodjon.robotcallv2.agent.tts.PcmChunkListener;
import uz.murodjon.robotcallv2.agent.tts.TtsProperties;
import uz.murodjon.robotcallv2.agent.tts.TtsRouter;
import uz.murodjon.robotcallv2.shared.dialog.DialogPhrases;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.voice.domain.entity.EffectiveVoiceSettings;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
    private final VoiceEmotionResolver emotionResolver;

    public SpeechOutput(DialogProperties props, TtsProperties ttsProps, TtsRouter ttsRouter,
                        VoiceMetrics metrics, DialogExecutors executors, VoiceEmotionResolver emotionResolver) {
        this.props = props;
        this.ttsProps = ttsProps;
        this.ttsRouter = ttsRouter;
        this.metrics = metrics;
        this.executors = executors;
        this.emotionResolver = emotionResolver;
    }

    /**
     * Synthesize one piece of the reply and queue it behind whatever is already playing.
     * Called once per sentence while streaming, or once for the whole reply otherwise.
     * Failures are logged, never thrown: a lost sentence is better than an aborted turn.
     */
    public SpeechOutcome speak(DialogSession s, String text) {
        SpeechOutcome refused = refusal(s, text);
        if (refused != null) {
            return refused;
        }
        // After the fact guard, which matches the figures as the model wrote them, and
        // before anything reaches a synthesizer: a yyyy-MM-dd date read aloud is a run of
        // digits, and the prompt asking the model not to write one is not a guarantee.
        String spoken = SpokenDates.humanize(text, s.language());
        try {
            EffectiveVoiceSettings dynamicSettings = emotionResolver.resolve(s);
            // Only the turn's first sentence is still on the §1.3 turnaround clock — that
            // is the one place shaving a TTS network round trip actually moves the number
            // that matters (everything after it already overlaps LLM generation, §7.2).
            if (s.isFirstAudioPending()) {
                s.latency().ttsRequested();
                return speakStreaming(s, spoken, dynamicSettings);
            }
            short[] pcm = ttsRouter.synthesize(spoken, s.language(), s.ttsVoice(), dynamicSettings);
            return deliver(s, new PreparedLine(spoken, pcm, SpeechOutcome.SPOKEN));
        } catch (Exception e) {
            log.warn("TTS failed during dialog [{}]: {}", s.channelId(), e.getMessage());
            return SpeechOutcome.SKIPPED;
        }
    }

    /**
     * Synthesize a line now so it can be queued later, in its place in the reply
     * ({@link #deliver}).
     *
     * <p>Sentences used to be synthesized one after another on the turn's thread: the
     * second sentence's round trip only began once the first had come back, and since the
     * first is usually a one-word acknowledgement that plays in under a second, the caller
     * heard "Xo'p." and then 400-900 ms of nothing on every reply (measured on recorded
     * calls). Preparing each sentence as soon as the model has written it lets that round
     * trip overlap the sentence still playing.
     *
     * <p>Every check {@link #speak} makes is made here, so nothing can be queued that
     * {@code speak} would have refused — the fact guard included.
     */
    public PreparedLine prepare(DialogSession s, String text) {
        SpeechOutcome refused = refusal(s, text);
        if (refused != null) {
            return PreparedLine.refused(text, refused);
        }
        String spoken = SpokenDates.humanize(text, s.language());
        try {
            short[] pcm = ttsRouter.synthesize(spoken, s.language(), s.ttsVoice(), emotionResolver.resolve(s));
            return new PreparedLine(spoken, pcm, SpeechOutcome.SPOKEN);
        } catch (Exception e) {
            log.warn("TTS failed during dialog [{}]: {}", s.channelId(), e.getMessage());
            return PreparedLine.refused(text, SpeechOutcome.SKIPPED);
        }
    }

    /**
     * Queue a prepared line for the caller. Lines must be delivered in reply order — the
     * playback position is what later decides how much of each one was heard.
     */
    public SpeechOutcome deliver(DialogSession s, PreparedLine line) {
        if (line.outcome() != SpeechOutcome.SPOKEN) {
            return line.outcome();
        }
        if (s.isCancelled()) {
            return SpeechOutcome.SKIPPED; // barge-in landed while it was being synthesized
        }
        long startSample = s.endpoint().queuedSamples();
        s.endpoint().enqueuePcm(line.pcm());
        s.appendSpokenAudio(line.text(), startSample, line.pcm().length);
        return SpeechOutcome.SPOKEN;
    }

    /** Why {@code text} must not be spoken, or {@code null} when it may be. */
    private SpeechOutcome refusal(DialogSession s, String text) {
        if (!ttsProps.enabled() || text == null || text.isBlank()) {
            return SpeechOutcome.SKIPPED;
        }
        if (s.isCancelled()) {
            // Checked before synthesis, not after: the caller is talking, and paying a TTS
            // round trip for audio that is discarded on arrival delays whatever the next
            // turn wants to say by exactly that round trip.
            return SpeechOutcome.SKIPPED;
        }
        if (SystemPromptFactory.isSystemNote(text) || SpeechSanitizer.isUnspeakable(text)) {
            // The model recited a note addressed to it or a technical/code token instead of answering.
            // Nothing in it is for the caller. The turn's own fallback line covers it.
            log.warn("[{}] refused to speak unspeakable or system note token in {}: {}",
                    s.channelId(), s.state(), text);
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
        return null;
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
    private SpeechOutcome speakStreaming(DialogSession s, String text, EffectiveVoiceSettings dynamicSettings) {
        AtomicBoolean any = new AtomicBoolean(false);
        AtomicLong startSample = new AtomicLong();
        AtomicInteger queued = new AtomicInteger();
        PcmChunkListener onChunk = pcm -> {
            if (s.isCancelled()) {
                return; // barge-in landed mid-stream; drain without queuing more audio
            }
            if (any.compareAndSet(false, true)) {
                startSample.set(s.endpoint().queuedSamples());
                // Before the enqueue, not after: this also clears isFirstAudioPending(),
                // which is what tells a filler still in flight on another thread that the
                // gap is closed. Doing it afterwards left a window where the filler could
                // re-check, see silence, and queue "bir soniya" behind the real opening.
                recordTurnaround(s); // first chunk of this turn's first sentence
            }
            s.endpoint().enqueuePcm(pcm);
            queued.addAndGet(pcm.length);
        };
        try {
            ttsRouter.synthesizeStreaming(text, s.language(), s.ttsVoice(), dynamicSettings, onChunk);
        } catch (Exception e) {
            log.warn("TTS streaming failed during dialog [{}]: {}", s.channelId(), e.getMessage());
        }
        // Whatever reached the endpoint before a mid-stream barge-in still counts as
        // spoken, same as any other sentence already queued when cancellation lands.
        if (!any.get()) {
            return SpeechOutcome.SKIPPED;
        }
        // Recorded with the audio it occupies rather than as "said": a barge-in three words
        // in queued the whole sentence and the caller heard three words of it, and which is
        // true is settled by the endpoint's playback position, not here (DialogSession
        // .spokenText). The distinction matters because the difference is a fact — a sum, a
        // due date — the model either believes it delivered or comes back to.
        s.appendSpokenAudio(text, startSample.get(), queued.get());
        return SpeechOutcome.SPOKEN;
    }

    private void recordTurnaround(DialogSession s) {
        Duration turnaround = s.takeTurnaround();
        if (turnaround == null) {
            return;
        }
        metrics.recordTurnaround(turnaround);
        // And onto the call's own row. The histogram above is process-wide and resets with
        // the process; this is the only place the number survives per call, and without it
        // call_technical.avg/max_turn_latency_ms was null on every call ever recorded —
        // the one figure §1.3 is written in terms of, missing from the table that reports it.
        s.recordTurnLatency(turnaround.toMillis());
        // The same moment, broken down: the budget number says how long the caller waited,
        // this says what for (TurnLatency). One line per turn, at INFO, because reading it
        // off a live call is the point — a histogram only answers the question afterwards.
        String stages = s.latency().finish(metrics);
        if (stages != null) {
            log.info("[{}] {}", s.channelId(), stages);
        }
    }

    /**
     * Synthesize a sentence into the TTS cache before any turn has asked for it.
     *
     * <p>Speculation ({@link Speculation}) buys back the endpointing silence for the LLM
     * but stops at the text: the first sentence still pays a full synthesis round trip
     * after the caller has finished, and that round trip is the last thing standing
     * between them and the first word. Synthesizing it early puts the audio in
     * {@link uz.murodjon.robotcallv2.agent.tts.TtsCache}, where the turn's own
     * {@link #speak} finds it as an ordinary cache hit — no second audio path, and nothing
     * can be spoken from here, so the fact guard still sees every sentence before the
     * caller does.
     *
     * <p>Two ways it is wasted, both costing characters and nothing else: the caller says
     * something the guess did not expect, or their tone shifts between now and the turn
     * (the voice settings are part of the cache key, and
     * {@link VoiceEmotionResolver} reads a sentiment this turn has not recorded yet).
     * Watch {@code voice.tts.chars.synthesized} against {@code voice.tts.chars.saved}.
     */
    public void warmSentence(DialogSession s, String text) {
        if (!ttsProps.enabled() || text == null || text.isBlank() || s.isEnded() || s.isCancelled()
                || SpeechSanitizer.isUnspeakable(text)) {
            return;
        }
        try {
            // Warmed in the form speak() will ask for, or the cache key would not match
            // and the round trip this exists to save is paid anyway.
            String spoken = SpokenDates.humanize(text, s.language());
            ttsRouter.synthesize(spoken, s.language(), s.ttsVoice(), emotionResolver.resolve(s));
            log.debug("[{}] pre-synthesized a speculative first sentence: {}", s.channelId(), spoken);
        } catch (Exception e) {
            log.debug("[{}] speculative synthesis failed: {}", s.channelId(), e.getMessage());
        }
    }

    /**
     * Say "I'm listening" over a caller who has been talking for a while — quietly, and
     * without taking the floor.
     *
     * <p>A person listening to a long answer says "aha" into it every few seconds, and a
     * line that stays perfectly silent while somebody explains their situation reads as
     * nobody being there. This is the one thing the bot says that is not a turn: it is not
     * synthesized as a reply, not recorded as spoken, and never enters the history — the
     * model must not learn that it said anything here, because it did not.
     *
     * <p>Played at a fraction of the normal amplitude. At full volume it stops being a
     * backchannel and starts being the bot talking over the caller, which is precisely the
     * behaviour {@link Backchannels} exists to keep the caller from doing to us.
     *
     * @param turn which of this call's turns the caller is in the middle of — also what
     *             picks the phrase, so the same one is not repeated twice running
     */
    public void speakBackchannel(DialogSession s, int turn) {
        if (props.backchannelAfterMs() <= 0 || !ttsProps.enabled() || !backchannelStillWanted(s)) {
            return;
        }
        try {
            List<String> options = DialogPhrases.backchannels(s.language());
            String line = options.get(Math.floorMod(turn, options.size()));
            short[] pcm = ttsRouter.synthesize(line, s.language(), s.ttsVoice(), emotionResolver.resolve(s));
            if (!backchannelStillWanted(s)) {
                return; // the caller finished while this was being synthesized
            }
            s.endpoint().enqueuePcm(attenuate(pcm, props.backchannelVolumePercent()));
            metrics.backchannelPlayed();
            log.debug("[{}] backchannel over a long answer: {}", s.channelId(), line);
        } catch (Exception e) {
            log.debug("[{}] backchannel failed: {}", s.channelId(), e.getMessage());
        }
    }

    /**
     * Step into an answer that has run long past the point where an operator would have.
     *
     * <p>Not the same act as a backchannel, and deliberately not quiet: this one is meant
     * to take the floor. A caller who has been explaining for half a minute has usually
     * stopped answering the question, and the recognizer cannot close an utterance that
     * never pauses — the turn sits waiting, the call clock runs, and nothing happens. An
     * apology and a check is how a person gets out of it.
     *
     * <p>Like a backchannel it is not recorded as a turn: what the caller says next is what
     * the model will answer, and it will answer it in full.
     */
    public void interject(DialogSession s) {
        if (props.interjectAfterMs() <= 0 || !ttsProps.enabled() || !backchannelStillWanted(s)) {
            return;
        }
        try {
            String line = DialogPhrases.interjection(s.language());
            short[] pcm = ttsRouter.synthesize(line, s.language(), s.ttsVoice(), emotionResolver.resolve(s));
            if (!backchannelStillWanted(s)) {
                return;
            }
            s.endpoint().enqueuePcm(pcm);
            metrics.interjected();
            log.info("[{}] caller has been talking for over {} ms — cutting in: {}",
                    s.channelId(), props.interjectAfterMs(), line);
        } catch (Exception e) {
            log.debug("[{}] interjection failed: {}", s.channelId(), e.getMessage());
        }
    }

    /**
     * Whether the caller is still in the middle of the answer this backchannel was meant
     * for. Anything the bot is already saying wins: a backchannel queued behind a real
     * line lands after it, where it makes no sense at all.
     */
    private boolean backchannelStillWanted(DialogSession s) {
        return !s.isEnded() && !s.busy().get() && !s.isCancelled() && !s.endpoint().isPlaying();
    }

    /** Quieten a line so it sits under the caller's voice instead of over it. */
    private static short[] attenuate(short[] pcm, int percent) {
        int level = Math.min(Math.max(percent, 1), 100);
        short[] quiet = new short[pcm.length];
        for (int i = 0; i < pcm.length; i++) {
            quiet[i] = (short) (pcm[i] * level / 100);
        }
        return quiet;
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
     * <p>Deliberately not free of charge to the clock, so it is rationed: only after
     * {@code filler-delay-ms} of actual silence, and only on a turn a caller is waiting on
     * (never the greeting — {@link DialogSession#isFirstAudioPending()} is false there).
     *
     * <p>Consecutive turns are no longer ruled out. They were, and that is what left the
     * middle of a four-turn call in three and a half seconds of silence: the wait is that
     * long on <em>every</em> turn here, so a turn's right to cover it cannot depend on what
     * the previous turn happened to need. Repetition is handled where it actually lives —
     * {@link #speakFiller} indexes the phrase by turn number, so consecutive fillers are
     * never the same words.
     */
    public ScheduledFuture<?> scheduleFiller(DialogSession s) {
        int turn = s.turnCount();
        if (props.fillerDelayMs() <= 0 || !ttsProps.enabled() || !s.isFirstAudioPending()) {
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
            EffectiveVoiceSettings dynamicSettings = emotionResolver.resolve(s);
            short[] pcm = ttsRouter.synthesize(line, s.language(), s.ttsVoice(), dynamicSettings);
            if (!fillerStillWanted(s)) {
                return;
            }
            s.endpoint().enqueuePcm(pcm);
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
