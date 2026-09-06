package uz.murodjon.robotcallv2.agent.audio;

import uz.murodjon.robotcallv2.aiagent.domain.enums.AmbientSound;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * The room the bot is calling from, mixed under its voice on the outbound leg.
 *
 * <p>Everything here is noise driven. The first version built its call-centre "chatter"
 * out of eight steady sine tones at voice formant frequencies, and eight steady sines are
 * a chord, not a room — a listener names it as synthetic inside a second. Speech has no
 * steady partials: it is broadband excitation shaped by moving resonances, and what makes
 * distant babble read as people is the <em>syllable rhythm</em>, not the pitch. So a
 * talker here is white noise through three formant resonators, gated by its own phrase and
 * syllable envelope, and a room is several of those at rates that never line up. Key
 * clicks are noise transients for the same reason — a decaying sine is a beep.
 *
 * <p>Levels are much lower than they were: ~ -38 dBFS rather than ~ -30. Synthesized TTS
 * speech sits around -20 dBFS, so the old bed was only ~10 dB under the bot's own voice
 * where a real office is 20-25 dB under. That margin is also what keeps the bed from
 * coming back up the line: the caller's handset leaks whatever we play into its
 * microphone, and a continuous voice-band signal arriving at the recognizer is exactly
 * what makes it return words nobody said.
 *
 * <p>Everything is band-limited to the telephone passband before calibration — energy
 * outside 250-3400 Hz never reaches an earpiece, so leaving it in only steals level from
 * the part that does.
 */
public final class AmbientSoundGenerator {

    private static final int SAMPLE_RATE = 8000;
    /**
     * Longer than a call's quiet stretches, because a loop is heard as a loop the moment
     * it comes round twice. At 10 s a 56 s call played the same babble, the same keystroke
     * and the same pause 5.7 times over, and a caller reads that repetition as the bed
     * cutting out and starting again. Sixty seconds costs 960 KB per soundscape and puts
     * the seam beyond most calls entirely.
     */
    private static final int LOOP_DURATION_SECONDS = 60;
    private static final int LOOP_SAMPLES = SAMPLE_RATE * LOOP_DURATION_SECONDS; // 480,000 samples

    /** Telephone passband — what the caller's earpiece can actually reproduce. */
    private static final double HIGH_PASS_HZ = 250.0;
    private static final double LOW_PASS_HZ = 3400.0;

    /** Cosine cross-fade at the loop boundary, 50 ms. */
    private static final int CROSSFADE_SAMPLES = 400;

    private static final Map<AmbientSound, short[]> SOUNDSCAPES = new EnumMap<>(AmbientSound.class);

    static {
        for (AmbientSound sound : AmbientSound.values()) {
            if (sound != AmbientSound.OFF) {
                SOUNDSCAPES.put(sound, generateLoop(sound));
            }
        }
    }

    private AmbientSoundGenerator() {}

    /**
     * Builds every soundscape now instead of on the first frame that needs one.
     *
     * <p>The static initializer above takes ~0.6s at this loop length. Left alone it runs
     * on whichever thread first calls {@link #mix}, and that thread is the RTP pacer's
     * Netty event loop — 0.6s of stalled outbound audio at the start of the first call
     * after a restart. Called from startup, it costs nothing anyone hears.
     */
    public static void warmUp() {
        SOUNDSCAPES.size();
    }

    /**
     * Mixes ambient sound into an outbound 8kHz 16-bit PCM frame in-place or returns a mixed frame.
     *
     * @param pcmFrame       the original 20ms frame (160 samples)
     * @param sound          the target ambient soundscape
     * @param samplePosition the running sample offset in the continuous call stream
     * @return 16-bit PCM frame with ambient audio mixed in
     */
    public static short[] mix(short[] pcmFrame, AmbientSound sound, long samplePosition) {
        if (sound == null || sound == AmbientSound.OFF || pcmFrame == null || pcmFrame.length == 0) {
            return pcmFrame;
        }
        short[] loop = SOUNDSCAPES.get(sound);
        if (loop == null || loop.length == 0) {
            return pcmFrame;
        }

        short[] output = new short[pcmFrame.length];
        int loopLen = loop.length;
        for (int i = 0; i < pcmFrame.length; i++) {
            int loopIndex = (int) ((samplePosition + i) % loopLen);
            int mixed = pcmFrame[i] + loop[loopIndex];
            // Clamp to 16-bit signed range
            output[i] = (short) Math.max(-32768, Math.min(32767, mixed));
        }
        return output;
    }

    /** Builds one soundscape's looping buffer, band-limited and calibrated to its target level. */
    private static short[] generateLoop(AmbientSound sound) {
        Random rand = new Random(sound.ordinal() * 31337L + 42);
        double[] raw = new double[LOOP_SAMPLES];
        double targetRms;

        switch (sound) {
            case CALL_CENTER -> {
                targetRms = 420.0; // ~ -37.9 dBFS
                addBabble(raw, rand, 7, 480.0, 1.0);
                addNoise(raw, rand, 0.20);          // room tone and ventilation
                addKeyboard(raw, rand, 0.12, 0.35, 0.55);
            }
            case OFFICE -> {
                targetRms = 300.0; // ~ -40.8 dBFS
                addNoise(raw, rand, 0.55);          // HVAC is most of a quiet office
                addBabble(raw, rand, 2, 450.0, 0.30);
                addKeyboard(raw, rand, 0.8, 1.5, 0.35);
            }
            case CAFE -> {
                targetRms = 380.0; // ~ -38.7 dBFS
                addBabble(raw, rand, 10, 430.0, 1.0);
                addNoise(raw, rand, 0.30);
                addCrockery(raw, rand);
            }
            case NATURAL_LINE -> {
                targetRms = 240.0; // ~ -42.7 dBFS — comfort noise, nothing more
                addNoise(raw, rand, 1.0);
                addLineHum(raw);
            }
            // OFF, and any constant added later that has no bed of its own.
            default -> {
                return new short[0];
            }
        }

        bandLimit(raw);
        short[] buffer = calibrate(raw, targetRms);
        crossFadeLoop(buffer);
        return buffer;
    }

    /**
     * Distant multi-talker babble: {@code talkers} independent voices, each white noise
     * through three formant resonators and gated by its own phrase/syllable envelope.
     *
     * @param baseFormantHz centre the talkers' first formant is drawn around; the higher
     *                      two are derived per talker so no two share a spectrum
     */
    private static void addBabble(double[] raw, Random rand, int talkers, double baseFormantHz, double level) {
        double[] excitation = new double[LOOP_SAMPLES];
        double[] voice = new double[LOOP_SAMPLES];
        for (int t = 0; t < talkers; t++) {
            for (int i = 0; i < LOOP_SAMPLES; i++) {
                excitation[i] = rand.nextDouble() * 2.0 - 1.0;
                voice[i] = 0.0;
            }
            double f1 = baseFormantHz * (0.75 + rand.nextDouble() * 0.6);
            double f2 = f1 * (2.0 + rand.nextDouble() * 1.6);
            double f3 = Math.min(3200.0, f2 * (1.4 + rand.nextDouble() * 0.7));
            resonate(voice, excitation, f1, 130.0, 1.0);
            resonate(voice, excitation, f2, 190.0, 0.55);
            resonate(voice, excitation, f3, 280.0, 0.30);

            double[] envelope = syllables(rand);
            double gain = level * (0.6 + rand.nextDouble() * 0.8) / Math.sqrt(talkers);
            for (int i = 0; i < LOOP_SAMPLES; i++) {
                raw[i] += voice[i] * envelope[i] * gain;
            }
        }
    }

    /**
     * How loud one talker is, moment to moment: phrases of 0.6-2.5 s at 3-6 syllables per
     * second, separated by pauses. Independent rates per talker are what stop the sum from
     * pulsing in step, which is the other half of why the sine version sounded mechanical.
     */
    private static double[] syllables(Random rand) {
        double[] envelope = new double[LOOP_SAMPLES];
        int edge = SAMPLE_RATE / 20; // 50 ms in and out of a phrase
        int i = 0;
        double phase = rand.nextDouble() * 2.0 * Math.PI;
        while (i < LOOP_SAMPLES) {
            int phraseLength = (int) (SAMPLE_RATE * (0.6 + rand.nextDouble() * 1.9));
            int pauseLength = (int) (SAMPLE_RATE * (0.3 + rand.nextDouble() * 1.2));
            double syllableRate = 3.0 + rand.nextDouble() * 3.0;
            for (int k = 0; k < phraseLength && i < LOOP_SAMPLES; k++, i++) {
                phase += 2.0 * Math.PI * syllableRate / SAMPLE_RATE;
                double syllable = 0.2 + 0.8 * Math.pow(0.5 * (1.0 - Math.cos(phase)), 1.6);
                double fade = Math.min(1.0, Math.min(k, phraseLength - k) / (double) edge);
                envelope[i] = syllable * fade;
            }
            i += pauseLength; // the gap between phrases stays zero
        }
        return envelope;
    }

    /** Pink noise: room tone, ventilation, and the line's own comfort noise. */
    private static void addNoise(double[] raw, Random rand, double level) {
        double b0 = 0, b1 = 0, b2 = 0;
        for (int i = 0; i < LOOP_SAMPLES; i++) {
            double white = rand.nextDouble() * 2.0 - 1.0;
            b0 = 0.99765 * b0 + white * 0.0990460;
            b1 = 0.96300 * b1 + white * 0.2965164;
            b2 = 0.57000 * b2 + white * 1.0526913;
            raw[i] += (b0 + b1 + b2 + white * 0.1848) * 0.25 * level;
        }
    }

    /** Keystrokes at {@code minGapSeconds}-{@code maxGapSeconds} intervals. */
    private static void addKeyboard(double[] raw, Random rand,
                                    double minGapSeconds, double maxGapSeconds, double level) {
        int i = SAMPLE_RATE / 10;
        while (i < LOOP_SAMPLES) {
            addTransient(raw, rand, i, 1400.0 + rand.nextDouble() * 1400.0, 900.0,
                    0.0025, level * (0.6 + rand.nextDouble() * 0.7));
            i += (int) (SAMPLE_RATE * (minGapSeconds + rand.nextDouble() * (maxGapSeconds - minGapSeconds)));
        }
    }

    /** A cup or spoon every few seconds: two inharmonic partials, ringing rather than clicking. */
    private static void addCrockery(double[] raw, Random rand) {
        int i = SAMPLE_RATE;
        while (i < LOOP_SAMPLES) {
            double partial = 1700.0 + rand.nextDouble() * 900.0;
            addTransient(raw, rand, i, partial, 60.0, 0.012, 0.10);
            addTransient(raw, rand, i, partial * 1.63, 90.0, 0.008, 0.06);
            i += (int) (SAMPLE_RATE * (2.0 + rand.nextDouble() * 3.0));
        }
    }

    /**
     * One percussive event: a noise burst rung through a resonator. Broadband excitation is
     * what separates a key press from a beep — the resonator only says what was hit.
     *
     * @param decaySeconds time constant of the burst; short is a click, long is a chime
     */
    private static void addTransient(double[] raw, Random rand, int at,
                                     double freqHz, double bandwidthHz, double decaySeconds, double level) {
        int length = (int) (SAMPLE_RATE * 0.05);
        double[] burst = new double[length];
        double[] shaped = new double[length];
        for (int k = 0; k < length; k++) {
            burst[k] = (rand.nextDouble() * 2.0 - 1.0) * Math.exp(-k / (SAMPLE_RATE * decaySeconds));
        }
        resonate(shaped, burst, freqHz, bandwidthHz, 1.0);
        for (int k = 0; k < length && at + k < raw.length; k++) {
            raw[at + k] += shaped[k] * level;
        }
    }

    /** Mains hum on an analog line. Mostly below the passband, which is why it stays faint. */
    private static void addLineHum(double[] raw) {
        for (int i = 0; i < LOOP_SAMPLES; i++) {
            raw[i] += Math.sin(2.0 * Math.PI * 100.0 * i / SAMPLE_RATE) * 0.06
                    + Math.sin(2.0 * Math.PI * 150.0 * i / SAMPLE_RATE) * 0.03;
        }
    }

    /**
     * Two-pole resonator, accumulated into {@code out}: a peak at {@code freqHz} whose
     * width is {@code bandwidthHz}. Three of these on one noise source is a vowel.
     */
    private static void resonate(double[] out, double[] excitation,
                                 double freqHz, double bandwidthHz, double gain) {
        double r = Math.exp(-Math.PI * bandwidthHz / SAMPLE_RATE);
        double coefficient = 2.0 * r * Math.cos(2.0 * Math.PI * freqHz / SAMPLE_RATE);
        double y1 = 0, y2 = 0;
        for (int i = 0; i < excitation.length; i++) {
            double y = (1.0 - r) * excitation[i] + coefficient * y1 - r * r * y2;
            y2 = y1;
            y1 = y;
            out[i] += y * gain;
        }
    }

    /** Restricts the bed to what a telephone earpiece reproduces, 12 dB/octave each side. */
    private static void bandLimit(double[] signal) {
        highPass(signal);
        highPass(signal);
        lowPass(signal);
        lowPass(signal);
    }

    private static void highPass(double[] signal) {
        double rc = 1.0 / (2.0 * Math.PI * HIGH_PASS_HZ);
        double dt = 1.0 / SAMPLE_RATE;
        double alpha = rc / (rc + dt);
        double previousIn = signal[0];
        double previousOut = signal[0];
        for (int i = 1; i < signal.length; i++) {
            double in = signal[i];
            previousOut = alpha * (previousOut + in - previousIn);
            previousIn = in;
            signal[i] = previousOut;
        }
    }

    private static void lowPass(double[] signal) {
        double rc = 1.0 / (2.0 * Math.PI * LOW_PASS_HZ);
        double dt = 1.0 / SAMPLE_RATE;
        double alpha = dt / (rc + dt);
        double previousOut = signal[0];
        for (int i = 1; i < signal.length; i++) {
            previousOut += alpha * (signal[i] - previousOut);
            signal[i] = previousOut;
        }
    }

    /** Scales the bed to {@code targetRms} and converts to 16-bit PCM. */
    private static short[] calibrate(double[] raw, double targetRms) {
        double sumSquares = 0;
        for (double sample : raw) {
            sumSquares += sample * sample;
        }
        double currentRms = Math.sqrt(sumSquares / raw.length);
        double gain = currentRms > 0 ? targetRms / currentRms : 1.0;

        short[] buffer = new short[raw.length];
        for (int i = 0; i < raw.length; i++) {
            int scaled = (int) Math.round(raw[i] * gain);
            buffer[i] = (short) Math.max(-32768, Math.min(32767, scaled));
        }
        return buffer;
    }

    /** Cosine cross-fade so the seam between the end and the start of the loop is inaudible. */
    private static void crossFadeLoop(short[] buffer) {
        for (int i = 0; i < CROSSFADE_SAMPLES; i++) {
            double ratio = 0.5 * (1.0 - Math.cos(Math.PI * i / CROSSFADE_SAMPLES));
            short startSample = buffer[i];
            short endSample = buffer[buffer.length - CROSSFADE_SAMPLES + i];
            buffer[i] = (short) Math.round(startSample * ratio + endSample * (1.0 - ratio));
        }
    }
}
