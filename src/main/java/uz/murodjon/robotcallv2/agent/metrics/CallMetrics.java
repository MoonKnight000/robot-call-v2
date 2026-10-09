package uz.murodjon.robotcallv2.agent.metrics;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Call-level metrics (PROJECT.md §10 Bosqich 12): call and disposition counts, a live
 * active-call gauge, call duration, voicemail detections, SIP trunk registrations and the
 * automated quality review. Exposed via Actuator at {@code /actuator/prometheus}.
 */
@Component
public class CallMetrics {

    private final MeterRegistry registry;
    private final Counter calls;
    private final DistributionSummary qualityScore;
    private final Timer callDuration;
    private final AtomicInteger active = new AtomicInteger(0);
    private final Map<String, AtomicInteger> trunkRegistrations = new ConcurrentHashMap<>();

    public CallMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.calls = Counter.builder("voice.calls.total").register(registry);
        this.qualityScore = DistributionSummary.builder("voice.qa.score")
                .description("automated quality review of a finished call, 0-100")
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

    /**
     * An answering machine was detected (§8.6). {@code by} says
     * which detector caught it — {@code audio} (the speech-length heuristic) or
     * {@code transcript} (a carrier or machine greeting in the words) — so the two can be
     * compared before either is tuned.
     */
    public void voicemailDetected(String by) {
        Counter.builder("voice.calls.voicemail.detected")
                .description("calls an answering machine picked up")
                .tag("by", by)
                .register(registry)
                .increment();
    }

    public void recordCallDuration(long seconds) {
        callDuration.record(Duration.ofSeconds(Math.max(0, seconds)));
    }

    /**
     * One finished call's quality score, as judged by a second model
     * ({@code CallQualityJudge}). A distribution rather than an average: what matters is
     * the tail, and a handful of very bad calls is exactly what an average hides.
     */
    public void recordQualityScore(int score) {
        qualityScore.record(score);
    }

    /**
     * One thing the judge found wrong with a call. Tagged so a regression reads as one
     * flag rising — a prompt change that starts answering in the wrong language moves
     * {@code language} and nothing else.
     */
    public void qualityFlag(String flag) {
        Counter.builder("voice.qa.flags")
                .description("findings from the automated call-quality review")
                .tag("flag", flag)
                .register(registry)
                .increment();
    }

    /**
     * One SIP registration's state as Asterisk last reported it: 1 registered, 0 anything
     * else. A trunk that drops its registration fails every outbound call through it, and
     * the first sign otherwise is a campaign whose calls all end in seconds.
     */
    public void trunkRegistration(String registration, boolean registered) {
        String name = registration == null || registration.isBlank() ? "unknown" : registration;
        trunkRegistrations.computeIfAbsent(name, key -> {
            AtomicInteger state = new AtomicInteger();
            Gauge.builder("voice.trunk.registered", state, AtomicInteger::get)
                    .description("1 when the SIP registration is Registered, 0 otherwise")
                    .tag("registration", key)
                    .register(registry);
            return state;
        }).set(registered ? 1 : 0);
    }
}
