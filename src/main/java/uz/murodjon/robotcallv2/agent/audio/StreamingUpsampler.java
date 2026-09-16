package uz.murodjon.robotcallv2.agent.audio;

import java.util.Arrays;

/**
 * Band-limited interpolation of 8 kHz telephone audio up to the rate a recognizer or a
 * speech-to-speech engine insists on, for audio that arrives in 20 ms frames.
 *
 * <p>{@link Resampler#upsample8kTo16k} draws a straight line between neighbouring
 * samples. That is cheap and keeps the band, but a straight line is a poor low-pass
 * filter: the spectrum of the 8 kHz signal is mirrored into the new 4–8 kHz band and
 * only lightly damped. A recognizer trained on wideband speech hears that image as hiss
 * on every sibilant. This interpolates with a Blackman-windowed sinc instead — the image
 * is pushed down by some 50 dB — and carries the filter history across frames, so
 * nothing happens at a frame boundary.
 *
 * <p>Costs {@value #TAPS_PER_SIDE} samples of lookahead — about 2 ms at 8 kHz — and a
 * handful of multiply-adds per output sample. Not thread-safe; one instance per stream.
 */
public final class StreamingUpsampler {

    /** Input samples used on each side of an interpolated point. */
    private static final int TAPS_PER_SIDE = 16;

    private final int factor;
    /** {@code phases[p - 1][k]}: weight of input sample {@code j - TAPS_PER_SIDE + 1 + k} for output phase {@code p}. */
    private final double[][] phases;
    /** Ring of the last {@code 2 * TAPS_PER_SIDE} input samples. */
    private final short[] history = new short[2 * TAPS_PER_SIDE];
    private long received;

    private StreamingUpsampler(int factor) {
        this.factor = factor;
        this.phases = new double[factor - 1][2 * TAPS_PER_SIDE];
        for (int p = 1; p < factor; p++) {
            double sum = 0;
            for (int k = 0; k < 2 * TAPS_PER_SIDE; k++) {
                // Distance, in output samples, from input sample (j - TAPS + 1 + k) to the
                // point being interpolated, which sits p output samples after input j.
                double t = (k - TAPS_PER_SIDE + 1) * factor - p;
                double weight = sinc(t / factor) * blackman(t, TAPS_PER_SIDE * factor);
                phases[p - 1][k] = weight;
                sum += weight;
            }
            // Unity gain at DC for every phase, or a steady tone would ripple at the
            // output rate.
            for (int k = 0; k < 2 * TAPS_PER_SIDE; k++) {
                phases[p - 1][k] /= sum;
            }
        }
    }

    /** 8 kHz to 16 kHz. */
    public static StreamingUpsampler from8kTo16k() {
        return new StreamingUpsampler(2);
    }

    /** 8 kHz to 24 kHz. */
    public static StreamingUpsampler from8kTo24k() {
        return new StreamingUpsampler(3);
    }

    /**
     * An upsampler from 8 kHz to {@code targetRate}, or null when the target is 8 kHz
     * itself and the audio passes through untouched.
     *
     * @throws IllegalArgumentException for a rate this class cannot produce — audio
     *                                  labelled with the wrong rate is accepted on the wire
     *                                  and comes back as gibberish, so refusing is better
     */
    public static StreamingUpsampler from8kTo(int targetRate) {
        return switch (targetRate) {
            case 8000 -> null;
            case 16000 -> from8kTo16k();
            case 24000 -> from8kTo24k();
            default -> throw new IllegalArgumentException("no upsampler from 8 kHz to " + targetRate + " Hz");
        };
    }

    /**
     * Feed the next input samples and get back every output sample they complete. The
     * first call returns {@value #TAPS_PER_SIDE} input samples' worth less than it was
     * given; after that each call returns exactly {@code length * factor}.
     */
    public short[] push(short[] in, int length) {
        if (in == null || length <= 0) {
            return new short[0];
        }
        short[] out = new short[length * factor];
        int produced = 0;
        for (int i = 0; i < length; i++) {
            history[(int) (received % history.length)] = in[i];
            received++;
            // The newest sample (index received - 1) completes the interpolation around
            // input j, which needs TAPS_PER_SIDE samples after it.
            long j = received - 1 - TAPS_PER_SIDE;
            if (j < 0) {
                continue;
            }
            out[produced++] = sampleAt(j);
            for (int p = 1; p < factor; p++) {
                double acc = 0;
                double[] weights = phases[p - 1];
                for (int k = 0; k < weights.length; k++) {
                    acc += weights[k] * sampleAt(j - TAPS_PER_SIDE + 1 + k);
                }
                out[produced++] = clamp(acc);
            }
        }
        return produced == out.length ? out : Arrays.copyOf(out, produced);
    }

    /** Input sample {@code index}; silence before the stream started. */
    private short sampleAt(long index) {
        if (index < 0) {
            return 0;
        }
        return history[(int) (index % history.length)];
    }

    private static double sinc(double x) {
        if (x == 0) {
            return 1;
        }
        double px = Math.PI * x;
        return Math.sin(px) / px;
    }

    /** Blackman window centred on 0, zero at ±{@code halfWidth}. */
    private static double blackman(double t, double halfWidth) {
        if (Math.abs(t) >= halfWidth) {
            return 0;
        }
        double n = (t + halfWidth) / (2 * halfWidth);
        return 0.42 - 0.5 * Math.cos(2 * Math.PI * n) + 0.08 * Math.cos(4 * Math.PI * n);
    }

    private static short clamp(double value) {
        long rounded = Math.round(value);
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, rounded));
    }
}
