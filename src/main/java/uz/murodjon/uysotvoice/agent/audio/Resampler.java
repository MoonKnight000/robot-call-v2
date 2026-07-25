package uz.murodjon.uysotvoice.agent.audio;

/**
 * Simple linear-interpolation resampler for telephone audio. Pure functions —
 * unit tested without any I/O (PROJECT.md §8).
 *
 * <p>Google's {@code phone_call} model accepts native 8 kHz, so upsampling is
 * only used when the configured STT sample rate is 16 kHz or for providers that
 * require it.
 */
public final class Resampler {

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
        if (length <= 0) {
            return new short[0];
        }
        short[] out = new short[length * 2];
        for (int i = 0; i < length; i++) {
            short current = in[i];
            short next = (i + 1 < length) ? in[i + 1] : current;
            out[i * 2] = current;
            out[i * 2 + 1] = (short) ((current + next) / 2);
        }
        return out;
    }
}
