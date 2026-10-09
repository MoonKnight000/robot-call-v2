package uz.murodjon.robotcallv2.agent.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Speech-recognition metrics: errors and mid-call failovers, billed and withheld audio,
 * endpointing, and the turn detector's verdicts. Exposed via Actuator at
 * {@code /actuator/prometheus}.
 */
@Component
public class SttMetrics {

    private static final String FAILOVER = "voice.stt.failover";

    private final MeterRegistry registry;
    private final Counter sttErrors;
    private final Counter sttSecondsSent;
    private final Counter sttSecondsSkipped;
    private final Counter sttUtterancesEndpointed;
    private final Counter sttFinalsPromoted;
    private final DistributionSummary endpointingHangover;
    private final Counter turnsExtended;
    private final Counter turnsComplete;
    private final Counter turnsClosedEarly;

    public SttMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.sttErrors = Counter.builder("voice.stt.errors").register(registry);
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
        this.sttFinalsPromoted = Counter.builder("voice.stt.finals.promoted")
                .description("utterances whose last interim was taken as the final because the provider's was late")
                .register(registry);
        // Flat at post-roll-ms unless the adaptation is on. Once it is, this is the whole
        // story: a distribution that never leaves the maximum means callers keep talking
        // through the closes, and one that sits on the floor means the floor is too high
        // to be worth the setting.
        this.endpointingHangover = DistributionSummary.builder("voice.stt.endpointing.hangover")
                .description("silence a long utterance had to wait out before it was closed")
                .baseUnit("milliseconds")
                .publishPercentileHistogram()
                .register(registry);
        // Utterances Smart Turn said were not finished when the timer wanted to close
        // them. All of them means the model disagrees with the timer on every turn, which
        // is a threshold problem, not a caller problem; none of them means it is adding
        // latency to load a model that changes nothing.
        this.turnsExtended = Counter.builder("voice.stt.turn.extended")
                .description("utterances given extra silence because they sounded unfinished")
                .register(registry);
        this.turnsClosedEarly = Counter.builder("voice.stt.turn.closed.early")
                .description("utterances closed before their hangover ran out, on the detector's say-so")
                .register(registry);
        this.turnsComplete = Counter.builder("voice.stt.turn.complete")
                .description("utterances the turn detector agreed were finished")
                .register(registry);
    }

    public void sttError() {
        sttErrors.increment();
    }

    /**
     * The speech gate's verdict on one of its closes, settled once the reopen grace has
     * passed ({@link uz.murodjon.robotcallv2.agent.audio.SpeechGate}). {@code continued}
     * means the caller spoke again inside the grace — the close cut a sentence in half, and
     * the bot may already have answered the first half. Its share of all closes is the
     * error rate any end-of-turn change has to hold or lower.
     */
    public void gateCloseJudged(boolean continued) {
        Counter.builder("voice.stt.gate.close")
                .description("speech gate closes, by whether the caller carried on within the reopen grace")
                .tag("outcome", continued ? "continued" : "ended")
                .register(registry)
                .increment();
    }

    /**
     * A live call moved off a recognizer that kept failing
     * ({@link uz.murodjon.robotcallv2.agent.stt.SttStreamBridge}). Tagged both ways: the
     * question this answers during an incident is which vendor is down, and one that only
     * counted events would need the logs to say.
     */
    public void sttFailover(String from, String to) {
        Counter.builder(FAILOVER)
                .description("calls moved to another STT provider mid-call after repeated failures")
                .tag("from", from != null ? from : "unknown")
                .tag("to", to != null ? to : "unknown")
                .register(registry)
                .increment();
    }

    /** Failovers since startup, summed over every from/to pair — a fleet-wide total for alerting. */
    public double countFailovers() {
        return registry.find(FAILOVER).counters().stream().mapToDouble(Counter::count).sum();
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

    /** The provider's final was late and the last interim was used in its place. */
    public void sttFinalPromoted() {
        sttFinalsPromoted.increment();
    }

    /** The wait a long utterance earned before it was closed — fixed, or learned. */
    public void sttEndpointingHangover(int ms) {
        endpointingHangover.record(ms);
    }

    /** The turn detector's verdict on one utterance the timer was ready to close. */
    public void turnScored(boolean complete) {
        (complete ? turnsComplete : turnsExtended).increment();
    }

    /**
     * An utterance the detector was sure of, closed before its hangover had run out. The
     * saving is only real while callers are not talking through these closes, so read it
     * against the turn count and against how often callers get a second turn they did not
     * mean to start.
     */
    public void turnClosedEarly() {
        turnsClosedEarly.increment();
    }
}
