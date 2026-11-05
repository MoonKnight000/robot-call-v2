package uz.murodjon.robotcallv2.agent.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Speech-synthesis metrics: errors, failovers, cache efficiency, billed characters and
 * synthesis latency. Exposed via Actuator at {@code /actuator/prometheus}.
 */
@Component
public class TtsMetrics {

    private final MeterRegistry registry;
    private final Counter ttsErrors;
    private final Counter ttsCacheHits;
    private final Counter ttsCacheMisses;
    private final Counter ttsCharsSynthesized;
    private final Counter ttsCharsSaved;
    private final Counter ttsFailovers;
    private final Timer ttsSynth;

    public TtsMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.ttsErrors = Counter.builder("voice.tts.errors").register(registry);
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
        // A provider outage that the substitute covered. Never zero for long without
        // someone looking: the caller is hearing a different voice than the campaign chose.
        this.ttsFailovers = Counter.builder("voice.tts.failovers")
                .description("lines a second TTS provider spoke after the first one failed")
                .register(registry);
        this.ttsSynth = Timer.builder("voice.tts.synth.latency").register(registry);
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

    /** A line the preferred TTS provider failed to speak was spoken by another one. */
    public void ttsFailover() {
        ttsFailovers.increment();
    }

    /** Failovers since startup — a fleet-wide total for alerting. */
    public double countFailovers() {
        return ttsFailovers.count();
    }

    /**
     * Running cache totals, read as deltas between two checks: hits over hits plus misses
     * says whether the cache is paying for itself.
     */
    public long ttsCacheHitCount() {
        return (long) ttsCacheHits.count();
    }

    public long ttsCacheMissCount() {
        return (long) ttsCacheMisses.count();
    }

    /**
     * Time from asking a provider to speak a line to its first audible PCM — the wait that is
     * the provider's and the network's, before any of our own queueing. Unlike
     * {@code voice.tts.synth.latency}, which spans the whole sentence, and the turn's
     * {@code tts_ttfb}, which also counts our hand-off, this stops at the first sample that
     * is not silence (a WAV header or a leading run of zeros would otherwise read as "fast").
     * A cache hit and a failed or fallen-back line record nothing.
     */
    public void ttsFirstAudio(String provider, String language, long nanos) {
        Timer.builder("voice.tts.ttfa")
                .description("provider request to first audible PCM chunk")
                .tag("provider", provider)
                .tag("language", language)
                .publishPercentileHistogram()
                .register(registry)
                .record(nanos, TimeUnit.NANOSECONDS);
    }

    /**
     * A streamed line got no audible audio inside {@code voice-agent.tts.stream.first-chunk-timeout-ms}
     * and was handed to the fallback provider. A provider that keeps showing up here is down
     * or has regressed, whatever its status endpoint says.
     */
    public void ttsFirstChunkTimeout(String provider) {
        Counter.builder("voice.tts.first.chunk.timeouts")
                .description("streamed lines abandoned because the provider produced no audio in time")
                .tag("provider", provider)
                .register(registry)
                .increment();
    }

    /** Which vendor region served a line ({@code x-region} on ElevenLabs): a handful of values, safe as a tag. */
    public void ttsRegion(String provider, String region) {
        Counter.builder("voice.tts.region")
                .description("lines by the vendor region that synthesized them")
                .tag("provider", provider)
                .tag("region", region)
                .register(registry)
                .increment();
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void stopTtsSynth(Timer.Sample sample) {
        sample.stop(ttsSynth);
    }
}
