package uz.murodjon.robotcallv2.agent.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

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

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void stopTtsSynth(Timer.Sample sample) {
        sample.stop(ttsSynth);
    }
}
