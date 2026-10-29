package uz.murodjon.robotcallv2.agent.audio;

import uz.murodjon.robotcallv2.agent.rtp.WavAudio;
import uz.murodjon.robotcallv2.agent.rtp.WavReader;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AmbientSound;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * The room the bot is calling from, mixed under its voice on the outbound leg.
 *
 * <p>Supports both pre-recorded studio 8 kHz WAV beds (from {@code /ambience/<name>.wav}
 * or {@code data/ambience/<name>.wav}) and highly realistic procedural soundscape synthesis.
 *
 * <p>Procedural chatter uses dynamic vowel formant modulation across phonetic targets
 * ([a], [e], [i], [o], [u]) with natural phrase cadence and independent vocal tract scaling,
 * sounding like genuine human speech murmur rather than static organ whistlers.
 * Typing sounds use dual-component acoustic transients (tactile switch strike + lower chassis
 * bottom-out thud) grouped into natural word bursts with inter-burst pauses. Room tone incorporates
 * subtle HVAC air movement and warm duct resonance.
 *
 * <p>Everything is band-limited to the telephone passband (250-3400 Hz) before calibration
 * to ~ -38 dBFS (~20 dB under TTS speech) to eliminate recognizer bleed.
 */
public final class AmbientSoundGenerator {

    private static final int SAMPLE_RATE = 8000;
    /**
     * Longer than a call's quiet stretches, because a loop is heard as a loop the moment
     * it comes round twice. Sixty seconds puts the loop seam beyond almost all calls.
     */
    private static final int LOOP_DURATION_SECONDS = 60;
    private static final int LOOP_SAMPLES = SAMPLE_RATE * LOOP_DURATION_SECONDS; // 480,000 samples

    /** Telephone passband — what the caller's earpiece can actually reproduce. */
    private static final double HIGH_PASS_HZ = 250.0;
    private static final double LOW_PASS_HZ = 3400.0;

    /** Cosine cross-fade at the loop boundary, 50 ms. */
    private static final int CROSSFADE_SAMPLES = 400;

    /**
     * Acoustic phonetic vowel formant prototypes (F1, F2, F3 in Hz):
     * [a] (father), [e] (head), [i] (see), [o] (call), [u] (foot), [ae] (had), [uh] (but).
     */
    private static final double[][] VOWEL_FORMANTS = {
        { 730.0, 1100.0, 2450.0 }, // [a]
        { 530.0, 1850.0, 2500.0 }, // [e]
        { 280.0, 2250.0, 2850.0 }, // [i]
        { 550.0,  850.0, 2400.0 }, // [o]
        { 320.0,  880.0, 2250.0 }, // [u]
        { 660.0, 1720.0, 2410.0 }, // [ae]
        { 520.0, 1200.0, 2390.0 }  // [uh]
    };

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
        return mix(pcmFrame, sound, samplePosition, 1.0, 0.0);
    }

    /**
     * As {@link #mix(short[], AmbientSound, long)}, with the bed trimmed and faded in.
     *
     * @param volume multiplier on the calibrated level, clamped to 0.0-1.0
     * @param fadeInSeconds equal-power ramp from call start; 0 starts at full level
     */
    public static short[] mix(short[] pcmFrame, AmbientSound sound, long samplePosition,
                              double volume, double fadeInSeconds) {
        if (sound == null || sound == AmbientSound.OFF || pcmFrame == null || pcmFrame.length == 0) {
            return pcmFrame;
        }
        short[] loop = SOUNDSCAPES.get(sound);
        if (loop == null || loop.length == 0) {
            return pcmFrame;
        }
        double level = Math.max(0.0, Math.min(1.0, volume));
        if (level == 0.0) {
            return pcmFrame;
        }

        short[] output = new short[pcmFrame.length];
        int loopLen = loop.length;
        for (int i = 0; i < pcmFrame.length; i++) {
            long position = samplePosition + i;
            int loopIndex = (int) (position % loopLen);
            int mixed = pcmFrame[i] + (int) Math.round(loop[loopIndex] * level * fadeGain(position, fadeInSeconds));
            // Clamp to 16-bit signed range
            output[i] = (short) Math.max(-32768, Math.min(32767, mixed));
        }
        return output;
    }

    /**
     * Equal-power ramp: {@code sin(pi/2 * t)} rather than a straight line.
     */
    private static double fadeGain(long samplePosition, double fadeInSeconds) {
        if (fadeInSeconds <= 0.0) {
            return 1.0;
        }
        double fadeSamples = fadeInSeconds * SAMPLE_RATE;
        if (samplePosition >= fadeSamples) {
            return 1.0;
        }
        return Math.sin(Math.PI / 2.0 * (samplePosition / fadeSamples));
    }

    /** Builds one soundscape's looping buffer, band-limited and calibrated to its target level. */
    private static short[] generateLoop(AmbientSound sound) {
        double targetRms;
        switch (sound) {
            case CALL_CENTER -> targetRms = 420.0; // ~ -37.9 dBFS
            case OFFICE -> targetRms = 320.0;      // ~ -40.2 dBFS
            case CAFE -> targetRms = 380.0;        // ~ -38.7 dBFS
            case NATURAL_LINE -> targetRms = 220.0;// ~ -43.5 dBFS
            case KEYBOARD_TYPING -> targetRms = 320.0; // ~ -40.2 dBFS
            default -> {
                return new short[0];
            }
        }

        // 1. Try pre-recorded WAV if provided
        short[] preRecorded = loadPreRecordedWav(sound, targetRms);
        if (preRecorded != null) {
            return preRecorded;
        }

        // 2. Fall back to high-realism procedural synthesis
        Random rand = new Random(sound.ordinal() * 31337L + 42);
        double[] raw = new double[LOOP_SAMPLES];

        switch (sound) {
            case CALL_CENTER -> {
                addBabble(raw, rand, 7, 1.0);
                addNoise(raw, rand, 0.18);          // room tone and ventilation
                addKeyboard(raw, rand, 1.8, 4.5, 0.55);
                addPaperShuffle(raw, rand);
            }
            case OFFICE -> {
                addNoise(raw, rand, 0.18);          // gentle room tone
                addBabble(raw, rand, 4, 0.60);      // distant colleagues talking
                addKeyboard(raw, rand, 2.5, 6.5, 0.50); // realistic office typing bursts
                addPaperShuffle(raw, rand);         // subtle paper/desk rustle
            }
            case CAFE -> {
                addBabble(raw, rand, 10, 1.0);
                addNoise(raw, rand, 0.20);
                addCrockery(raw, rand);
            }
            case NATURAL_LINE -> {
                addNoise(raw, rand, 0.30);          // subtle line comfort noise
                addLineHum(raw);
            }
            case KEYBOARD_TYPING -> {
                addNoise(raw, rand, 0.15);
                addKeyboard(raw, rand, 0.25, 0.75, 1.0); // active note-taking typing
            }
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
     * Attempts to load a pre-recorded 8kHz 16-bit mono WAV file for the given soundscape.
     * Looks first in classpath (/ambience/<name>.wav), then in data/ambience/<name>.wav.
     * Returns null if no file exists or if reading fails, falling back to procedural synthesis.
     */
    private static short[] loadPreRecordedWav(AmbientSound sound, double targetRms) {
        String name = sound.name().toLowerCase();
        byte[] bytes = null;

        // 1. Try classpath resource: /ambience/<name>.wav
        try (InputStream in = AmbientSoundGenerator.class.getResourceAsStream("/ambience/" + name + ".wav")) {
            if (in != null) {
                bytes = in.readAllBytes();
            }
        } catch (Exception ignored) {
        }

        // 2. Try filesystem: data/ambience/<name>.wav
        if (bytes == null) {
            Path fsPath = Path.of("data", "ambience", name + ".wav");
            if (Files.isRegularFile(fsPath)) {
                try {
                    bytes = Files.readAllBytes(fsPath);
                } catch (Exception ignored) {
                }
            }
        }

        if (bytes == null || bytes.length == 0) {
            return null;
        }

        try {
            WavAudio audio = WavReader.read(bytes);
            if (audio.samples() == null || audio.samples().length == 0) {
                return null;
            }
            short[] src = audio.samples();
            double[] raw = new double[LOOP_SAMPLES];
            for (int i = 0; i < LOOP_SAMPLES; i++) {
                raw[i] = src[i % src.length];
            }
            bandLimit(raw);
            short[] buffer = calibrate(raw, targetRms);
            crossFadeLoop(buffer);
            return buffer;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Distant multi-talker babble: independent voices with dynamic formant modulation across
     * syllables, phrase pauses, and vocal tract variance.
     */
    private static void addBabble(double[] raw, Random rand, int talkers, double level) {
        double r1 = Math.exp(-Math.PI * 130.0 / SAMPLE_RATE);
        double r2 = Math.exp(-Math.PI * 190.0 / SAMPLE_RATE);
        double r3 = Math.exp(-Math.PI * 280.0 / SAMPLE_RATE);
        double r1Sq = r1 * r1;
        double r2Sq = r2 * r2;
        double r3Sq = r3 * r3;

        for (int t = 0; t < talkers; t++) {
            double tractScale = 0.85 + rand.nextDouble() * 0.32;
            double gain = level * (0.65 + rand.nextDouble() * 0.70) / Math.sqrt(talkers);

            // Filter states
            double y1_1 = 0, y2_1 = 0;
            double y1_2 = 0, y2_2 = 0;
            double y1_3 = 0, y2_3 = 0;

            int vIdx = rand.nextInt(VOWEL_FORMANTS.length);
            double targetF1 = VOWEL_FORMANTS[vIdx][0] * tractScale;
            double targetF2 = VOWEL_FORMANTS[vIdx][1] * tractScale;
            double targetF3 = Math.min(3350.0, VOWEL_FORMANTS[vIdx][2] * tractScale);

            double curF1 = targetF1;
            double curF2 = targetF2;
            double curF3 = targetF3;

            double c1 = 2.0 * r1 * Math.cos(2.0 * Math.PI * curF1 / SAMPLE_RATE);
            double c2 = 2.0 * r2 * Math.cos(2.0 * Math.PI * curF2 / SAMPLE_RATE);
            double c3 = 2.0 * r3 * Math.cos(2.0 * Math.PI * curF3 / SAMPLE_RATE);

            // Talker speech phrases and pauses
            int i = (int) (rand.nextDouble() * SAMPLE_RATE * 3); // initial stagger
            while (i < LOOP_SAMPLES) {
                int phraseLength = (int) (SAMPLE_RATE * (0.8 + rand.nextDouble() * 2.2));
                int pauseLength = (int) (SAMPLE_RATE * (0.4 + rand.nextDouble() * 1.5));
                double syllableRate = 3.5 + rand.nextDouble() * 2.5;
                int syllableSamples = Math.max(1, (int) (SAMPLE_RATE / syllableRate));
                int edge = (int) (SAMPLE_RATE * 0.045); // 45ms phrase attack/decay

                for (int k = 0; k < phraseLength && i < LOOP_SAMPLES; k++, i++) {
                    // At start of each syllable, pick a new vowel target
                    if (k % syllableSamples == 0) {
                        vIdx = rand.nextInt(VOWEL_FORMANTS.length);
                        targetF1 = VOWEL_FORMANTS[vIdx][0] * tractScale;
                        targetF2 = VOWEL_FORMANTS[vIdx][1] * tractScale;
                        targetF3 = Math.min(3350.0, VOWEL_FORMANTS[vIdx][2] * tractScale);
                    }

                    // Update resonant frequencies smoothly
                    if ((k & 7) == 0) {
                        curF1 += (targetF1 - curF1) * 0.06;
                        curF2 += (targetF2 - curF2) * 0.06;
                        curF3 += (targetF3 - curF3) * 0.06;
                        c1 = 2.0 * r1 * Math.cos(2.0 * Math.PI * curF1 / SAMPLE_RATE);
                        c2 = 2.0 * r2 * Math.cos(2.0 * Math.PI * curF2 / SAMPLE_RATE);
                        c3 = 2.0 * r3 * Math.cos(2.0 * Math.PI * curF3 / SAMPLE_RATE);
                    }

                    double white = rand.nextDouble() * 2.0 - 1.0;

                    // 3-pole formant filtering
                    double out1 = (1.0 - r1) * white + c1 * y1_1 - r1Sq * y2_1;
                    y2_1 = y1_1;
                    y1_1 = out1;

                    double out2 = (1.0 - r2) * white + c2 * y1_2 - r2Sq * y2_2;
                    y2_2 = y1_2;
                    y1_2 = out2;

                    double out3 = (1.0 - r3) * white + c3 * y1_3 - r3Sq * y2_3;
                    y2_3 = y1_3;
                    y1_3 = out3;

                    double voiceSample = out1 + out2 * 0.55 + out3 * 0.28;

                    // Phrase envelope (in/out fade)
                    double phraseFade = Math.min(1.0, Math.min(k, phraseLength - k) / (double) edge);

                    // Syllable modulation
                    double sylPhase = 2.0 * Math.PI * (k % syllableSamples) / (double) syllableSamples;
                    double sylAmp = 0.25 + 0.75 * Math.pow(0.5 * (1.0 - Math.cos(sylPhase)), 1.5);

                    raw[i] += voiceSample * phraseFade * sylAmp * gain;
                }

                // Pauses between phrases
                i += pauseLength;
            }
        }
    }

    /** Pink noise: room tone, ventilation, and the line's own comfort noise. */
    private static void addNoise(double[] raw, Random rand, double level) {
        double b0 = 0, b1 = 0, b2 = 0;
        for (int i = 0; i < LOOP_SAMPLES; i++) {
            double white = rand.nextDouble() * 2.0 - 1.0;
            b0 = 0.99765 * b0 + white * 0.0990460;
            b1 = 0.96300 * b1 + white * 0.2965164;
            b2 = 0.57000 * b2 + white * 1.0526913;
            double pink = (b0 + b1 + b2 + white * 0.1848) * 0.25;

            // Subtle indoor HVAC air movement (~122 Hz ventilation hum)
            double hvac = Math.sin(2.0 * Math.PI * 122.0 * i / SAMPLE_RATE) * 0.035;

            raw[i] += (pink + hvac) * level;
        }
    }

    /**
     * Realistic keyboard typing with natural word bursts (3-7 characters at 80-160ms intervals),
     * inter-burst pauses, dual-component keystroke acoustics (crisp tactile click + chassis bottom-out thud),
     * and distinct spacebar/enter resonance.
     */
    private static void addKeyboard(double[] raw, Random rand,
                                    double minBurstGapSeconds, double maxBurstGapSeconds, double level) {
        int i = (int) (SAMPLE_RATE * (minBurstGapSeconds * 0.5 + rand.nextDouble() * 0.5));
        while (i < LOOP_SAMPLES) {
            int burstCount = 3 + rand.nextInt(5); // 3 to 7 keystrokes in a typing burst (a word)
            for (int k = 0; k < burstCount && i < LOOP_SAMPLES; k++) {
                boolean spacebar = (k == burstCount - 1); // spacebar at the end of word
                addKeystroke(raw, rand, i, spacebar, level);

                if (!spacebar) {
                    // Gap between letters: 85 to 160 ms (approx 60-80 WPM typing speed)
                    i += (int) (SAMPLE_RATE * (0.085 + rand.nextDouble() * 0.075));
                } else {
                    // Small gap after spacebar before pause: 120-200 ms
                    i += (int) (SAMPLE_RATE * (0.12 + rand.nextDouble() * 0.08));
                }
            }
            // Pause between words or sentences
            i += (int) (SAMPLE_RATE * (minBurstGapSeconds + rand.nextDouble() * (maxBurstGapSeconds - minBurstGapSeconds)));
        }
    }

    /** Dual-component keystroke: high-frequency tactile strike + low-mid chassis bottoming out. */
    private static void addKeystroke(double[] raw, Random rand, int at, boolean spacebar, double level) {
        if (at >= raw.length) {
            return;
        }
        double clickFreq = spacebar ? 1700.0 + rand.nextDouble() * 500.0 : 2300.0 + rand.nextDouble() * 900.0;
        double clickDecay = spacebar ? 0.0035 : 0.0022;
        double clickLevel = level * (spacebar ? 0.85 : 0.95) * (0.8 + rand.nextDouble() * 0.4);

        double thudFreq = spacebar ? 360.0 + rand.nextDouble() * 100.0 : 490.0 + rand.nextDouble() * 140.0;
        double thudDecay = spacebar ? 0.012 : 0.0075;
        double thudLevel = level * (spacebar ? 0.65 : 0.40) * (0.8 + rand.nextDouble() * 0.4);

        addTransient(raw, rand, at, clickFreq, 850.0, clickDecay, clickLevel);
        addTransient(raw, rand, at, thudFreq, 380.0, thudDecay, thudLevel);
    }

    /** Cafe tableware: ceramic inharmonic clinks + occasional saucer placement thud. */
    private static void addCrockery(double[] raw, Random rand) {
        int i = SAMPLE_RATE;
        while (i < LOOP_SAMPLES) {
            double partial = 1750.0 + rand.nextDouble() * 800.0;
            // High ceramic chime
            addTransient(raw, rand, i, partial, 55.0, 0.015, 0.09);
            addTransient(raw, rand, i, partial * 1.58, 85.0, 0.009, 0.05);

            // Occasional dull saucer clunk
            if (rand.nextDouble() < 0.4) {
                addTransient(raw, rand, i, 480.0 + rand.nextDouble() * 120.0, 320.0, 0.012, 0.07);
            }

            i += (int) (SAMPLE_RATE * (2.2 + rand.nextDouble() * 3.5));
        }
    }

    /** Occasional subtle desk movement or paper shuffle with smooth friction envelope. */
    private static void addPaperShuffle(double[] raw, Random rand) {
        int i = (int) (SAMPLE_RATE * (2.0 + rand.nextDouble() * 3.0));
        while (i < LOOP_SAMPLES) {
            int duration = (int) (SAMPLE_RATE * (0.11 + rand.nextDouble() * 0.12)); // 110-230 ms
            double freq = 1200.0 + rand.nextDouble() * 800.0; // 1200-2000 Hz paper friction
            double level = 0.10 + rand.nextDouble() * 0.05;

            double[] burst = new double[duration];
            double[] shaped = new double[duration];
            for (int k = 0; k < duration; k++) {
                // Hann-windowed friction envelope
                double window = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * k / (double) duration));
                burst[k] = (rand.nextDouble() * 2.0 - 1.0) * window;
            }
            resonate(shaped, burst, freq, 650.0, 1.0);
            for (int k = 0; k < duration && i + k < raw.length; k++) {
                raw[i + k] += shaped[k] * level;
            }

            i += (int) (SAMPLE_RATE * (4.5 + rand.nextDouble() * 6.5));
        }
    }

    /**
     * One percussive event: a noise burst rung through a resonator.
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
     * width is {@code bandwidthHz}.
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
