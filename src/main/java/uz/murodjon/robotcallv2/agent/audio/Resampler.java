package uz.murodjon.robotcallv2.agent.audio;

/**
 * High-performance, anti-aliased resampler for telephone and voice agent audio (PROJECT.md §8).
 * Pure functions — zero allocations on empty input, runs with Q15 fixed-point arithmetic
 * on the real-time audio loop.
 *
 * <p>Standard decimation (e.g. naive box averaging or sample skipping) folds frequencies
 * above the Nyquist limit (4 kHz) back into the telephone band, causing unpleasant metallic
 * ringing/hissing on sibilants ("s", "sh", "ch", "z"). This implementation uses symmetric
 * polyphase/FIR low-pass filtering to attenuate ultrasonic aliasing while preserving
 * crisp, natural voice clarity.
 */
public final class Resampler {

    // 7-tap symmetric FIR low-pass filter coefficients for 16 kHz -> 8 kHz (Q15 fixed point, sum = 32768)
    private static final int[] FIR_16K_TO_8K = {-1024, 1638, 9216, 13108, 9216, 1638, -1024};
    private static final int FIR_16K_OFFSET = 3;

    // 9-tap symmetric FIR low-pass filter coefficients for 24 kHz -> 8 kHz (Q15 fixed point, sum = 32768)
    private static final int[] FIR_24K_TO_8K = {-700, 400, 3600, 7400, 11368, 7400, 3600, 400, -700};
    private static final int FIR_24K_OFFSET = 4;

    private Resampler() {
    }

    /**
     * Upsample 8 kHz PCM to 16 kHz by inserting one linearly interpolated sample
     * between each pair of input samples (2x).
     *
     * @param in     source samples at 8 kHz
     * @param length number of valid samples in {@code in}
     * @return a new array of {@code length * 2} samples at 16 kHz
     */
    public static short[] upsample8kTo16k(short[] in, int length) {
        if (in == null || length <= 0) {
            return new short[0];
        }
        int validLen = Math.min(in.length, length);
        short[] out = new short[validLen * 2];
        for (int i = 0; i < validLen; i++) {
            short current = in[i];
            short next = (i + 1 < validLen) ? in[i + 1] : current;
            out[i * 2] = current;
            out[i * 2 + 1] = (short) ((current + next) / 2);
        }
        return out;
    }

    /**
     * Downsample 16 kHz PCM to the 8 kHz telephone rate using an anti-aliasing symmetric FIR
     * low-pass filter (cutoff ~3.6 kHz).
     *
     * <p>Eliminates metallic high-frequency aliasing on telephone audio codecs (G.711 / μ-law).
     *
     * @param in     source samples at 16 kHz
     * @param length number of valid samples in {@code in}
     * @return a new array of {@code length / 2} samples at 8 kHz
     */
    public static short[] downsample16kTo8k(short[] in, int length) {
        if (in == null || length <= 0) {
            return new short[0];
        }
        int validLen = Math.min(in.length, length);
        int outLength = validLen / 2;
        if (outLength <= 0) {
            return new short[0];
        }
        short[] out = new short[outLength];
        for (int i = 0; i < outLength; i++) {
            int center = i * 2;
            int acc = 0;
            for (int k = 0; k < FIR_16K_TO_8K.length; k++) {
                int srcIdx = center + (k - FIR_16K_OFFSET);
                if (srcIdx < 0) {
                    srcIdx = 0;
                } else if (srcIdx >= validLen) {
                    srcIdx = validLen - 1;
                }
                acc += FIR_16K_TO_8K[k] * in[srcIdx];
            }
            int sample = (acc + 16384) >> 15; // round & scale back from Q15
            out[i] = clampToShort(sample);
        }
        return out;
    }

    /**
     * Downsample 24 kHz PCM to the 8 kHz telephone rate (3:1 decimation) using an anti-aliasing
     * symmetric FIR low-pass filter (cutoff ~3.6 kHz).
     *
     * <p>Used for realtime speech-to-speech engines (Gemini Live, Moshi, Qwen-Omni) which stream
     * 24 kHz audio while the Asterisk RTP leg operates at 8 kHz.
     *
     * @param in     source samples at 24 kHz
     * @param length number of valid samples in {@code in}
     * @return a new array of {@code length / 3} samples at 8 kHz
     */
    public static short[] downsample24kTo8k(short[] in, int length) {
        if (in == null || length <= 0) {
            return new short[0];
        }
        int validLen = Math.min(in.length, length);
        int outLength = validLen / 3;
        if (outLength <= 0) {
            return new short[0];
        }
        short[] out = new short[outLength];
        for (int i = 0; i < outLength; i++) {
            int center = i * 3 + 1;
            int acc = 0;
            for (int k = 0; k < FIR_24K_TO_8K.length; k++) {
                int srcIdx = center + (k - FIR_24K_OFFSET);
                if (srcIdx < 0) {
                    srcIdx = 0;
                } else if (srcIdx >= validLen) {
                    srcIdx = validLen - 1;
                }
                acc += FIR_24K_TO_8K[k] * in[srcIdx];
            }
            int sample = (acc + 16384) >> 15;
            out[i] = clampToShort(sample);
        }
        return out;
    }

    private static short clampToShort(int val) {
        if (val > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        if (val < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        return (short) val;
    }
}
