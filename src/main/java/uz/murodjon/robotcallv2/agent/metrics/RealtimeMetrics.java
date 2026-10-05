package uz.murodjon.robotcallv2.agent.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Realtime (speech-to-speech) engine metrics, tagged by engine: latency, tokens, session
 * time, reply language, reconnects, fallbacks, interruptions and echo suppression.
 * Exposed via Actuator at {@code /actuator/prometheus}.
 */
@Component
public class RealtimeMetrics {

    private static final String FALLBACKS = "voice.realtime.fallbacks";

    private final MeterRegistry registry;

    public RealtimeMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * The realtime counterpart of {@link DialogMetrics#recordTurnaround}: the caller stopped
     * talking (measured by our own VAD, not the engine's) until the first audio of the
     * engine's answer was queued. Our VAD rather than the vendor's endpointer so that
     * engines — and the cascade pipeline — are compared on the same clock; tagged by engine
     * because that comparison is the point of having it.
     */
    public void recordRealtimeTurnaround(String engine, Duration elapsed) {
        Timer.builder("voice.realtime.turnaround.latency")
                .description("caller stopped talking -> first audio of the realtime engine's answer queued")
                .tag("engine", tagValue(engine))
                .publishPercentileHistogram()
                .publishPercentiles(DialogMetrics.SLO_PERCENTILE)
                .register(registry)
                .record(elapsed);
    }

    /**
     * Opening line to first audio on a realtime call — how long the caller sat on a
     * freshly answered, silent line. The worst place for latency: they have not heard
     * anything yet and are deciding whether anyone is there.
     */
    public void recordRealtimeFirstAudio(String engine, Duration elapsed) {
        Timer.builder("voice.realtime.first.audio.latency")
                .description("realtime call answered -> first audio of the engine queued")
                .tag("engine", tagValue(engine))
                .publishPercentileHistogram()
                .register(registry)
                .record(elapsed);
    }

    /** How long one tool call held a realtime engine's answer — heard by the caller as silence. */
    public void recordRealtimeToolLatency(String tool, Duration elapsed) {
        Timer.builder("voice.realtime.tool.latency")
                .description("realtime engine asked for a tool -> result sent back")
                .tag("tool", tagValue(tool))
                .publishPercentileHistogram()
                .register(registry)
                .record(elapsed);
    }

    /**
     * Tokens one realtime engine response cost. Kept apart from {@code voice.llm.tokens.*}:
     * those drive the cascade pipeline's cache-efficiency check, and audio tokens priced
     * at a different rate would only muddy it.
     */
    public void recordRealtimeUsage(String engine, long prompt, long completion, long cached) {
        realtimeTokens(engine, "prompt", prompt);
        realtimeTokens(engine, "completion", completion);
        realtimeTokens(engine, "cached", cached);
    }

    private void realtimeTokens(String engine, String kind, long tokens) {
        if (tokens <= 0) {
            return;
        }
        Counter.builder("voice.realtime.tokens")
                .description("tokens reported by a realtime engine")
                .tag("engine", tagValue(engine))
                .tag("kind", kind)
                .register(registry)
                .increment(tokens);
    }

    /** Connected seconds on a realtime engine — the unit speech-to-speech vendors bill in. */
    public void realtimeSessionSeconds(String engine, long seconds) {
        if (seconds <= 0) {
            return;
        }
        Counter.builder("voice.realtime.session.seconds")
                .description("seconds a realtime engine session was open")
                .tag("engine", tagValue(engine))
                .register(registry)
                .increment(seconds);
    }

    /**
     * The language one spoken realtime reply was in, as far as the script detector can
     * tell. The prompt rule "answer in the caller's language" has no other witness: a
     * rising {@code ru} share on an uz campaign is the rule failing.
     */
    public void realtimeLanguage(String engine, String language) {
        Counter.builder("voice.realtime.language")
                .description("realtime agent replies by detected language")
                .tag("engine", tagValue(engine))
                .tag("lang", tagValue(language))
                .register(registry)
                .increment();
    }

    /** The engine dropped and resumed the same conversation on a new connection. */
    public void realtimeReconnect(String engine) {
        Counter.builder("voice.realtime.reconnects")
                .description("realtime sessions resumed on a new connection mid-call")
                .tag("engine", tagValue(engine))
                .register(registry)
                .increment();
    }

    /**
     * The engine could not be kept and the call continued on the cascade pipeline — or,
     * when that was not possible, was hung up. Should be rare; every one is a caller who
     * heard the voice change mid-call.
     */
    public void realtimeFallback(String engine) {
        Counter.builder(FALLBACKS)
                .description("realtime calls that lost their engine and fell back to the cascade pipeline")
                .tag("engine", tagValue(engine))
                .register(registry)
                .increment();
    }

    /** Fallbacks since startup, summed over every engine — a fleet-wide total for alerting. */
    public double countFallbacks() {
        return registry.find(FALLBACKS).counters().stream().mapToDouble(Counter::count).sum();
    }

    /** The caller talked over a realtime engine and it stopped — the counterpart of {@code voice.dialog.barge.in}. */
    public void realtimeInterruption(String engine) {
        Counter.builder("voice.realtime.interruptions")
                .description("realtime engine replies the caller interrupted")
                .tag("engine", tagValue(engine))
                .register(registry)
                .increment();
    }

    /**
     * Caller audio withheld from a realtime engine because it was only the bot's own voice
     * coming back down the line. Non-zero on most calls means the far end has no echo
     * cancellation; zero everywhere means the gate is doing nothing and could be off.
     */
    public void realtimeEchoSuppressed(double seconds) {
        if (seconds <= 0) {
            return;
        }
        Counter.builder("voice.realtime.echo.suppressed.seconds")
                .description("seconds of caller audio attenuated as echo of the bot's own voice")
                .register(registry)
                .increment(seconds);
    }

    private static String tagValue(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
