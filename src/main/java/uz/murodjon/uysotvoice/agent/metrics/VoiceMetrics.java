package uz.murodjon.uysotvoice.agent.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.shared.dialog.Disposition;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Micrometer metrics for the voice pipeline (PROJECT.md §10 Bosqich 12): call and
 * disposition counts, STT/TTS/LLM error counters, LLM-turn and TTS-synthesis
 * latency timers, inbound RTP quality, call duration, and a live active-call gauge.
 * Exposed via Actuator at {@code /actuator/prometheus}.
 */
@Component
public class VoiceMetrics {

    /** The percentile the §1.3 turnaround budget is judged on. */
    private static final double SLO_PERCENTILE = 0.95;

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
    private final Counter sttUtterancesEndpointed;
    private final Counter noInputPrompts;
    private final Counter noInputHangups;
    private final Counter factGuardBlocks;
    private final Counter voicemailsDetected;
    private final Counter spokenLineRetries;
    private final Counter fillersPlayed;
    private final Counter ttsFailovers;
    private final Counter rtpPacketsReceived;
    private final Counter rtpPacketsLost;
    private final Counter rtpPacketsReordered;
    private final Counter rtpSilentCalls;
    private final DistributionSummary rtpJitter;
    private final DistributionSummary rtpLoss;
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
        // Zero while the provider's own detector is in charge. Once it isn't, this should
        // track the number of caller turns: far fewer means utterances are being run
        // together, far more means they are being chopped up.
        this.sttUtterancesEndpointed = Counter.builder("voice.stt.utterances.endpointed")
                .description("utterances this side declared finished, instead of the provider")
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
        // Each one is a second LLM round trip inside a live turn (DialogEngine.streamTurn),
        // so this is the direct read-out on whether the prompt is getting the model to
        // speak and call a tool in the same breath. Watch it against voice.turnaround.latency.
        this.spokenLineRetries = Counter.builder("voice.llm.spoken.line.retries")
                .description("turns the model answered with tool calls only, forcing a second request")
                .register(registry);
        // How often a turn was slow enough that the caller was given something to listen
        // to. Read as a share of voice.llm.turn.latency's count: a few percent is the
        // feature working, most turns means the pipeline is slow and this is papering
        // over it — fix the latency, do not lengthen the filler.
        this.fillersPlayed = Counter.builder("voice.dialog.filler.played")
                .description("turns where a short filler covered the wait for the LLM")
                .register(registry);
        // A provider outage that the substitute covered. Never zero for long without
        // someone looking: the caller is hearing a different voice than the campaign chose.
        this.ttsFailovers = Counter.builder("voice.tts.failovers")
                .description("lines a second TTS provider spoke after the first one failed")
                .register(registry);
        // Inbound stream quality (RFC 3550). The counters are the fleet-wide view; the
        // two summaries below are per call, which is where a handful of bad calls inside
        // an otherwise healthy total becomes visible.
        this.rtpPacketsReceived = Counter.builder("voice.rtp.packets.received").register(registry);
        this.rtpPacketsLost = Counter.builder("voice.rtp.packets.lost")
                .description("packets the sequence numbers say the network never delivered")
                .register(registry);
        this.rtpPacketsReordered = Counter.builder("voice.rtp.packets.reordered")
                .description("packets that arrived late or twice")
                .register(registry);
        // Should be zero. Anything else is a media path that was never established —
        // the caller talked to a bot that could not hear a thing (docs/NETWORK.md).
        this.rtpSilentCalls = Counter.builder("voice.rtp.calls.silent")
                .description("calls that ended without a single inbound RTP packet")
                .register(registry);
        this.rtpJitter = DistributionSummary.builder("voice.rtp.jitter")
                .description("interarrival jitter of one call's inbound RTP")
                .baseUnit("milliseconds")
                .publishPercentileHistogram()
                .register(registry);
        this.rtpLoss = DistributionSummary.builder("voice.rtp.loss")
                .description("packet loss of one call's inbound RTP")
                .baseUnit("percent")
                .publishPercentileHistogram()
                .register(registry);
        this.llmTurn = Timer.builder("voice.llm.turn.latency").publishPercentileHistogram().register(registry);
        this.ttsSynth = Timer.builder("voice.tts.synth.latency").register(registry);
        // The number §1.3 actually budgets (<1000ms): client stopped speaking -> first
        // bot audio on the wire. The LLM and TTS timers alone cannot show it, because
        // sentence streaming overlaps them.
        this.turnaround = Timer.builder("voice.turnaround.latency")
                .description("client final transcript -> first byte of bot audio queued")
                .publishPercentileHistogram()
                // Also computed in-process, because the §1.3 budget is checked here
                // (AlertingService) and not only in whatever scrapes Prometheus.
                .publishPercentiles(SLO_PERCENTILE)
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

    /** A turn produced tool calls but no speakable text, so it had to be asked again. */
    public void spokenLineRetry() {
        spokenLineRetries.increment();
    }

    /** A short filler was played because the turn was leaving the caller in silence. */
    public void fillerPlayed() {
        fillersPlayed.increment();
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

    /** We told the recognizer an utterance was over instead of letting it decide (§1.3). */
    public void sttUtteranceEnded() {
        sttUtterancesEndpointed.increment();
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

    /** A line the preferred TTS provider failed to speak was spoken by another one. */
    public void ttsFailover() {
        ttsFailovers.increment();
    }

    /**
     * Record one finished call's inbound RTP quality. A call with no packets at all is
     * only counted as silent: it has no loss ratio (nothing was expected either) and no
     * jitter, and averaging those zeros in would hide exactly the failure being counted.
     */
    public void recordRtpQuality(long received, long lost, long reordered, double jitterMillis) {
        if (received <= 0) {
            rtpSilentCalls.increment();
            return;
        }
        rtpPacketsReceived.increment(received);
        if (lost > 0) {
            rtpPacketsLost.increment(lost);
        }
        if (reordered > 0) {
            rtpPacketsReordered.increment(reordered);
        }
        rtpJitter.record(jitterMillis);
        rtpLoss.record(lost * 100.0 / (received + lost));
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    /** @return elapsed nanoseconds, so callers can also accumulate this per-call (§10.5 "Texnik" tab). */
    public long stopLlmTurn(Timer.Sample sample) {
        return sample.stop(llmTurn);
    }

    public void stopTtsSynth(Timer.Sample sample) {
        sample.stop(ttsSynth);
    }

    /** Record how long the caller waited between finishing their turn and hearing the bot. */
    public void recordTurnaround(Duration elapsed) {
        turnaround.record(elapsed);
    }

    /**
     * The 95th percentile turnaround in milliseconds, or {@code 0} before anything has
     * been measured. Micrometer keeps this over a rolling window (its default statistic
     * expiry), so it answers "how is the pipeline doing now", not "since startup" — which
     * is what an alert needs.
     */
    public double turnaroundP95Millis() {
        for (ValueAtPercentile value : turnaround.takeSnapshot().percentileValues()) {
            if (value.percentile() == SLO_PERCENTILE) {
                return value.value(TimeUnit.MILLISECONDS);
            }
        }
        return 0;
    }

    /**
     * Turns measured since startup. Cumulative on purpose: the caller compares it against
     * its own previous reading to learn how many turns the latest window actually holds,
     * which no windowed count of ours could tell it.
     */
    public long turnaroundCount() {
        return turnaround.count();
    }

    public void recordCallDuration(long seconds) {
        callDuration.record(Duration.ofSeconds(Math.max(0, seconds)));
    }
}
