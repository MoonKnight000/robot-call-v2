package uz.murodjon.uysotvoice.agent.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.shared.dialog.Disposition;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Micrometer metrics for the voice pipeline (PROJECT.md §10 Bosqich 12): call and
 * disposition counts, STT/TTS/LLM error counters, LLM-turn and TTS-synthesis
 * latency timers, call duration, and a live active-call gauge. Exposed via Actuator
 * at {@code /actuator/prometheus}.
 */
@Component
public class VoiceMetrics {

    private final MeterRegistry registry;
    private final Counter calls;
    private final Counter sttErrors;
    private final Counter ttsErrors;
    private final Counter llmErrors;
    private final Counter ttsCacheHits;
    private final Counter ttsCacheMisses;
    private final Counter ttsCharsSynthesized;
    private final Counter ttsCharsSaved;
    private final Counter llmPromptTokens;
    private final Counter llmCompletionTokens;
    private final Counter llmCachedTokens;
    private final Counter sttSecondsSent;
    private final Counter sttSecondsSkipped;
    private final Counter noInputPrompts;
    private final Counter noInputHangups;
    private final Counter factGuardBlocks;
    private final Counter voicemailsDetected;
    private final Timer llmTurn;
    private final Timer ttsSynth;
    private final Timer turnaround;
    private final Timer callDuration;
    private final AtomicInteger active = new AtomicInteger(0);

    public VoiceMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.calls = Counter.builder("voice.calls.total").register(registry);
        this.sttErrors = Counter.builder("voice.stt.errors").register(registry);
        this.ttsErrors = Counter.builder("voice.tts.errors").register(registry);
        this.llmErrors = Counter.builder("voice.llm.errors").register(registry);
        this.ttsCacheHits = Counter.builder("voice.tts.cache.hits").register(registry);
        this.ttsCacheMisses = Counter.builder("voice.tts.cache.misses").register(registry);
        // Yandex bills TTS per character, so characters — not requests — are the cost
        // unit. "saved" is what a cache hit did not have to buy.
        this.ttsCharsSynthesized = Counter.builder("voice.tts.chars.synthesized")
                .description("characters actually sent to a TTS provider (billed)")
                .register(registry);
        this.ttsCharsSaved = Counter.builder("voice.tts.chars.saved")
                .description("characters served from cache instead of being synthesized")
                .register(registry);
        // Token accounting for the LLM (§2.6). cached = the share of the prompt the
        // provider served from its context cache, i.e. the payoff of keeping the
        // request prefix stable; if this stays at zero, prefix caching is not working.
        this.llmPromptTokens = Counter.builder("voice.llm.tokens.prompt").register(registry);
        this.llmCompletionTokens = Counter.builder("voice.llm.tokens.completion").register(registry);
        this.llmCachedTokens = Counter.builder("voice.llm.tokens.cached")
                .description("prompt tokens the provider served from its context cache")
                .register(registry);
        // Yandex bills STT per streamed audio-second; "skipped" is what VAD gating kept
        // off the wire.
        this.sttSecondsSent = Counter.builder("voice.stt.audio.seconds.sent")
                .description("audio seconds streamed to the STT provider (billed)")
                .register(registry);
        this.sttSecondsSkipped = Counter.builder("voice.stt.audio.seconds.skipped")
                .description("audio seconds withheld from the STT provider by VAD gating")
                .register(registry);
        // A rising prompt count means callers are going quiet — a recognizer that stopped
        // emitting finals looks exactly like this, so it is worth watching.
        this.noInputPrompts = Counter.builder("voice.dialog.no.input.prompts")
                .description("times the bot asked whether the caller was still there")
                .register(registry);
        this.noInputHangups = Counter.builder("voice.dialog.no.input.hangups")
                .description("calls ended because the line stayed silent")
                .register(registry);
        // Should be zero. Anything else is the model trying to state a figure that is not
        // in the caller's file (§4.4) — alert on it rather than watch it.
        this.factGuardBlocks = Counter.builder("voice.dialog.fact.guard.blocks")
                .description("sentences withheld because their figures did not match the call facts")
                .register(registry);
        this.voicemailsDetected = Counter.builder("voice.calls.voicemail.detected")
                .description("calls cut short because an answering machine picked up")
                .register(registry);
        this.llmTurn = Timer.builder("voice.llm.turn.latency").publishPercentileHistogram().register(registry);
        this.ttsSynth = Timer.builder("voice.tts.synth.latency").register(registry);
        // The number §1.3 actually budgets (<1000ms): client stopped speaking -> first
        // bot audio on the wire. The LLM and TTS timers alone cannot show it, because
        // sentence streaming overlaps them.
        this.turnaround = Timer.builder("voice.turnaround.latency")
                .description("client final transcript -> first byte of bot audio queued")
                .publishPercentileHistogram()
                .register(registry);
        this.callDuration = Timer.builder("voice.call.duration").register(registry);
        Gauge.builder("voice.calls.active", active, AtomicInteger::get).register(registry);
    }

    public void callStarted() {
        calls.increment();
    }

    public void activeInc() {
        active.incrementAndGet();
    }

    public void activeDec() {
        active.updateAndGet(n -> n > 0 ? n - 1 : 0);
    }

    public int activeCalls() {
        return active.get();
    }

    /** Count the final disposition, tagged for a distribution breakdown. */
    public void disposition(Disposition d) {
        Counter.builder("voice.calls.disposition")
                .tag("disposition", d != null ? d.name() : "UNKNOWN")
                .register(registry)
                .increment();
    }

    public void sttError() {
        sttErrors.increment();
    }

    public void ttsError() {
        ttsErrors.increment();
    }

    public void ttsCacheHit() {
        ttsCacheHits.increment();
    }

    public void ttsCacheMiss() {
        ttsCacheMisses.increment();
    }

    /** Characters a provider was actually asked to synthesize (and billed for). */
    public void ttsCharsSynthesized(int chars) {
        if (chars > 0) {
            ttsCharsSynthesized.increment(chars);
        }
    }

    /** Characters a cache hit spared us from buying. */
    public void ttsCharsSaved(int chars) {
        if (chars > 0) {
            ttsCharsSaved.increment(chars);
        }
    }

    public void llmError() {
        llmErrors.increment();
    }

    /**
     * Record one LLM call's token usage. {@code cached} is the part of {@code prompt}
     * the provider served from its context cache — it is included in {@code prompt},
     * not additional to it.
     */
    public void recordLlmUsage(long prompt, long completion, long cached) {
        if (prompt > 0) {
            llmPromptTokens.increment(prompt);
        }
        if (completion > 0) {
            llmCompletionTokens.increment(completion);
        }
        if (cached > 0) {
            llmCachedTokens.increment(cached);
        }
    }

    /** Audio streamed to STT, in seconds — the unit Yandex bills. */
    public void sttAudioSent(double seconds) {
        if (seconds > 0) {
            sttSecondsSent.increment(seconds);
        }
    }

    /** Audio VAD gating kept off the STT stream, in seconds. */
    public void sttAudioSkipped(double seconds) {
        if (seconds > 0) {
            sttSecondsSkipped.increment(seconds);
        }
    }

    /** The bot asked whether the caller was still there. */
    public void noInputPrompt() {
        noInputPrompts.increment();
    }

    /** A call ended because nobody spoke. */
    public void noInputHangup() {
        noInputHangups.increment();
    }

    /** A sentence was withheld because its figures did not match the call facts (§4.4). */
    public void factGuardBlock() {
        factGuardBlocks.increment();
    }

    /** An answering machine was detected and the call cut short (§8.6). */
    public void voicemailDetected() {
        voicemailsDetected.increment();
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void stopLlmTurn(Timer.Sample sample) {
        sample.stop(llmTurn);
    }

    public void stopTtsSynth(Timer.Sample sample) {
        sample.stop(ttsSynth);
    }

    /** Record how long the caller waited between finishing their turn and hearing the bot. */
    public void recordTurnaround(Duration elapsed) {
        turnaround.record(elapsed);
    }

    public void recordCallDuration(long seconds) {
        callDuration.record(Duration.ofSeconds(Math.max(0, seconds)));
    }
}
