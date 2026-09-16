package uz.murodjon.robotcallv2.agent.audio;

/**
 * Brings a stream of synthesized speech to one steady level before it goes out as µ-law,
 * and keeps its peaks off the codec's ceiling.
 *
 * <p>Speech-to-speech engines do not agree on how loud they are, and neither does one
 * engine across a call: a whispered aside and an emphatic sentence can be 10 dB apart,
 * and the loud one reaches µ-law clipped. A caller hears the first as a bot that mumbles
 * and the second as distortion. This is a slow automatic gain control — it follows the
 * speech level over a few hundred milliseconds, not the syllables — with a soft limiter
 * behind it for whatever the gain cannot catch in time.
 *
 * <p>Silence is left alone: the level only learns from chunks that carry speech, so the
 * gain does not climb during a pause and blast the next word. Gain changes are ramped
 * across a chunk, never stepped, so there is no zipper noise at chunk boundaries.
 *
 * <p>Not thread-safe; one instance per call.
 */
public final class SpeechLevelNormalizer {

    /** −20 dBFS: telephone speech level, with headroom for µ-law. */
    private static final double TARGET_RMS = 3277;
    /** Below this a chunk is a pause or breath and teaches the level nothing. */
    private static final double SPEECH_RMS = 300;
    /** Never more than +10 dB — a near-silent engine is broken, not quiet. */
    private static final double MAX_GAIN = 3.16;
    /** Never less than −10 dB. */
    private static final double MIN_GAIN = 0.316;
    /** Weight of one chunk in the speech level; chunks are 20–40 ms, so this is a few hundred ms. */
    private static final double LEVEL_SMOOTHING = 0.1;
    /** Where the limiter starts bending the waveform: −2 dBFS. */
    private static final double KNEE = 26000;

    private double level;
    private double gain = 1.0;

    /**
     * Normalize one chunk in place and return it.
     *
     * @param pcm    16-bit samples at any rate
     * @param length valid samples in {@code pcm}
     */
    public short[] process(short[] pcm, int length) {
        if (pcm == null || length <= 0) {
            return pcm;
        }
        double sumSq = 0;
        for (int i = 0; i < length; i++) {
            sumSq += (double) pcm[i] * pcm[i];
        }
        double rms = Math.sqrt(sumSq / length);
        if (rms >= SPEECH_RMS) {
            level = level == 0 ? rms : level + LEVEL_SMOOTHING * (rms - level);
        }
        double targetGain = level == 0 ? 1.0 : Math.max(MIN_GAIN, Math.min(MAX_GAIN, TARGET_RMS / level));
        double startGain = gain;
        for (int i = 0; i < length; i++) {
            double g = startGain + (targetGain - startGain) * (i + 1) / length;
            pcm[i] = limit(pcm[i] * g);
        }
        gain = targetGain;
        return pcm;
    }

    /** Linear below the knee, bent smoothly towards full scale above it — never hard-clipped. */
    private static short limit(double sample) {
        double magnitude = Math.abs(sample);
        if (magnitude > KNEE) {
            double headroom = Short.MAX_VALUE - KNEE;
            magnitude = KNEE + headroom * Math.tanh((magnitude - KNEE) / headroom);
        }
        return (short) Math.round(Math.copySign(magnitude, sample));
    }
}
