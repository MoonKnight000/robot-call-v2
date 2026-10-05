package uz.murodjon.robotcallv2.agent.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Inbound media quality (RFC 3550): packet counts, per-call jitter and loss, silent calls
 * and the estimated MOS. Exposed via Actuator at {@code /actuator/prometheus}.
 */
@Component
public class RtpMetrics {

    private final Counter rtpPacketsReceived;
    private final Counter rtpPacketsLost;
    private final Counter rtpPacketsReordered;
    private final Counter rtpSilentCalls;
    private final DistributionSummary rtpJitter;
    private final DistributionSummary rtpLoss;
    private final DistributionSummary rtpMos;

    public RtpMetrics(MeterRegistry registry) {
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
        // A distribution for the same reason as jitter and loss: the calls worth looking
        // at are the tail.
        this.rtpMos = DistributionSummary.builder("voice.rtp.mos")
                .description("estimated mean opinion score of one call's inbound audio (1.0-4.4)")
                .publishPercentileHistogram()
                .register(registry);
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

    /** One finished call's estimated MOS ({@code MosEstimator}). */
    public void recordRtpMos(double mos) {
        rtpMos.record(mos);
    }

    /**
     * How many calls have been given a MOS so far, and the sum of their scores — the two
     * running totals an alert takes a windowed mean from. {0, 0} before the first call.
     */
    public double[] mosTotals() {
        return new double[]{rtpMos.count(), rtpMos.totalAmount()};
    }
}
