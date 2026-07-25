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
    private final Timer llmTurn;
    private final Timer ttsSynth;
    private final Timer callDuration;
    private final AtomicInteger active = new AtomicInteger(0);

    public VoiceMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.calls = Counter.builder("voice.calls.total").register(registry);
        this.sttErrors = Counter.builder("voice.stt.errors").register(registry);
        this.ttsErrors = Counter.builder("voice.tts.errors").register(registry);
        this.llmErrors = Counter.builder("voice.llm.errors").register(registry);
        this.llmTurn = Timer.builder("voice.llm.turn.latency").publishPercentileHistogram().register(registry);
        this.ttsSynth = Timer.builder("voice.tts.synth.latency").register(registry);
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

    public void llmError() {
        llmErrors.increment();
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

    public void recordCallDuration(long seconds) {
        callDuration.record(Duration.ofSeconds(Math.max(0, seconds)));
    }
}
