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

    /**
     * Downsample 16 kHz PCM to the 8 kHz telephone rate by averaging each pair of input
     * samples. The average is a crude low-pass: dropping every second sample outright
     * would fold everything above 4 kHz back into the audible band, which on synthesized
     * speech is heard as a metallic edge on sibilants.
     *
     * <p>Used for TTS providers that only return 16 kHz audio (Aisha), since the RTP path
     * plays 8 kHz.
     *
     * @param in     source samples at 16 kHz
     * @param length number of valid samples in {@code in}
     * @return a new array of {@code length / 2} samples at 8 kHz
     */
    public static short[] downsample16kTo8k(short[] in, int length) {
        if (length <= 0) {
            return new short[0];
        }
        short[] out = new short[length / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (short) ((in[i * 2] + in[i * 2 + 1]) / 2);
        }
        return out;
    }

    /**
     * Downsample 24 kHz PCM to the 8 kHz telephone rate by averaging each group of three
     * input samples — the same crude low-pass as {@link #downsample16kTo8k}, at 3:1.
     *
     * <p>Used for realtime engines, which listen at telephone quality and answer at
     * broadcast quality: Gemini Live takes 16 kHz in and always returns 24 kHz out
     * ({@code RealtimeProvider.outputSampleRate}), while the RTP leg plays 8 kHz.
     *
     * <p>A tail of one or two samples that does not fill a group is dropped rather than
     * averaged short: at 24 kHz that is at most 83 µs, and keeping the ratio exact
     * matters more than the tail, since these chunks are concatenated back to back and a
     * rounding-up would drift the stream longer than the speech it carries.
     *
     * @param in     source samples at 24 kHz
     * @param length number of valid samples in {@code in}
     * @return a new array of {@code length / 3} samples at 8 kHz
     */
    public static short[] downsample24kTo8k(short[] in, int length) {
        if (length <= 0) {
            return new short[0];
        }
        short[] out = new short[length / 3];
        for (int i = 0; i < out.length; i++) {
            int base = i * 3;
            out[i] = (short) ((in[base] + in[base + 1] + in[base + 2]) / 3);
        }
        return out;
    }
}
