package uz.murodjon.robotcallv2.agent.rtp;

/**
 * Mean Opinion Score estimate for one call's inbound audio, from the two numbers the
 * receive side actually has: packet loss and interarrival jitter.
 *
 * <p>A simplified ITU-T G.107 E-model, the same reduction most VoIP monitors use: an
 * R-factor is taken down by delay and loss, then mapped onto the 1–5 MOS scale. What it
 * is for is separating "the network chewed the audio" from "the agent said the wrong
 * thing" when a call is complained about — a call under 3.5 was hard to hear before any
 * model touched it. It is an estimate, not a measurement: nobody listened.
 *
 * <p>One-way delay is not observable from here (there is no RTCP round trip on the
 * externalMedia leg), so a fixed PSTN figure stands in for it. That makes the estimate a
 * function of loss and jitter only, which is exactly the part that varies call to call.
 */
public final class MosEstimator {

    /** A typical mobile/PSTN one-way path including the carrier's own buffering. */
    private static final double ASSUMED_ONE_WAY_DELAY_MS = 100.0;
    /** G.711 packetisation and codec delay. */
    private static final double CODEC_DELAY_MS = 10.0;
    /** The best a narrowband G.711 call can score; anything above is rounding. */
    private static final double MAX_NARROWBAND_MOS = 4.4;

    private MosEstimator() {
    }

    /**
     * @param lossPercent share of packets the sender emitted that never arrived, 0–100
     * @param jitterMs    smoothed interarrival jitter in milliseconds
     * @return MOS between 1.0 and {@value #MAX_NARROWBAND_MOS}
     */
    public static double estimate(double lossPercent, double jitterMs) {
        double effectiveLatency = ASSUMED_ONE_WAY_DELAY_MS + 2 * Math.max(0, jitterMs) + CODEC_DELAY_MS;
        double r = effectiveLatency < 160
                ? 93.2 - effectiveLatency / 40
                : 93.2 - (effectiveLatency - 120) / 10;
        r -= 2.5 * Math.max(0, lossPercent);
        r = Math.max(0, Math.min(100, r));
        double mos = 1 + 0.035 * r + 0.000007 * r * (r - 60) * (100 - r);
        return Math.max(1.0, Math.min(MAX_NARROWBAND_MOS, mos));
    }
}
