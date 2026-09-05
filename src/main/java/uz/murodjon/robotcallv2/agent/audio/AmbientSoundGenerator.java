package uz.murodjon.robotcallv2.agent.audio;

import uz.murodjon.robotcallv2.campaign.domain.enums.AmbientSound;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * Generates realistic ambient background soundscapes (OFF, OFFICE, CALL_CENTER, NATURAL_LINE, CAFE)
 * calibrated at approximately -30dB to -32dBFS in 8kHz 16-bit mono PCM.
 */
public final class AmbientSoundGenerator {

    private static final int SAMPLE_RATE = 8000;
    private static final int LOOP_DURATION_SECONDS = 10;
    private static final int LOOP_SAMPLES = SAMPLE_RATE * LOOP_DURATION_SECONDS; // 80,000 samples

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
     * Mixes ambient sound into an outbound 8kHz 16-bit PCM frame in-place or returns a mixed frame.
     *
     * @param pcmFrame       the original 20ms frame (160 samples)
     * @param sound          the target ambient soundscape
     * @param samplePosition the running sample offset in the continuous call stream
     * @return 16-bit PCM frame with ambient audio mixed in at ~ -30dBFS
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

    /**
     * Generates a realistic looping soundscape buffer calibrated to target RMS level.
     */
    private static short[] generateLoop(AmbientSound sound) {
        double[] raw = new double[LOOP_SAMPLES];
        Random rand = new Random(sound.ordinal() * 31337L + 42);
        double targetRms = 1000.0;

        switch (sound) {
            case CALL_CENTER -> {
                targetRms = 1100.0; // ~ -29.5 dBFS (audible, crisp call center ambiance)

                // 1. Distant multi-speaker babble / murmur (multiple human voice formant frequencies)
                double[] voiceFreqs = {175.0, 230.0, 310.0, 440.0, 580.0, 780.0, 1100.0, 1550.0};
                for (int i = 0; i < LOOP_SAMPLES; i++) {
                    double chatter = 0;
                    for (int s = 0; s < voiceFreqs.length; s++) {
                        // Slow, independent breathing / talking modulation rates (0.15Hz - 1.2Hz)
                        double mod = 0.5 + 0.5 * Math.sin(2.0 * Math.PI * (0.15 + s * 0.12) * i / SAMPLE_RATE);
                        chatter += Math.sin(2.0 * Math.PI * voiceFreqs[s] * i / SAMPLE_RATE) * mod;
                    }
                    raw[i] = chatter * 250.0;
                }

                // 2. Continuous soft room/ventilation noise (pink-filtered)
                double b0 = 0, b1 = 0, b2 = 0;
                for (int i = 0; i < LOOP_SAMPLES; i++) {
                    double white = rand.nextDouble() * 2.0 - 1.0;
                    b0 = 0.99765 * b0 + white * 0.0990460;
                    b1 = 0.96300 * b1 + white * 0.2965164;
                    b2 = 0.57000 * b2 + white * 1.0526913;
                    double pink = (b0 + b1 + b2 + white * 0.1848) * 120.0;
                    raw[i] += pink;
                }

                // 3. Realistic soft keyboard clicks (operator typing)
                int clickSpacing = (int) (SAMPLE_RATE * 0.18); // typing intervals
                for (int i = 500; i < LOOP_SAMPLES - 500; i += clickSpacing) {
                    if (rand.nextDouble() < 0.75) {
                        int burstLen = (int) (SAMPLE_RATE * 0.015); // 15ms click impulse
                        double clickFreq = 1800.0 + rand.nextDouble() * 1200.0;
                        for (int k = 0; k < burstLen && (i + k) < LOOP_SAMPLES; k++) {
                            double decay = Math.exp(-k / 15.0);
                            double clickSample = Math.sin(2.0 * Math.PI * clickFreq * k / SAMPLE_RATE) * decay * 1400.0;
                            raw[i + k] += clickSample;
                        }
                    }
                    // Randomize next click offset
                    clickSpacing = (int) (SAMPLE_RATE * (0.12 + rand.nextDouble() * 0.35));
                }
            }
            case OFFICE -> {
                targetRms = 850.0; // ~ -31.7 dBFS

                // Gentle room presence & low-frequency ventilation
                double b0 = 0, b1 = 0;
                for (int i = 0; i < LOOP_SAMPLES; i++) {
                    double white = rand.nextDouble() * 2.0 - 1.0;
                    b0 = 0.98 * b0 + white * 0.08;
                    b1 = 0.90 * b1 + white * 0.12;
                    raw[i] = (b0 + b1) * 350.0;
                }

                // Occasional subtle keystrokes or mouse clicks
                for (int i = 800; i < LOOP_SAMPLES - 500; i += (int) (SAMPLE_RATE * (0.8 + rand.nextDouble() * 1.5))) {
                    int burstLen = (int) (SAMPLE_RATE * 0.012);
                    for (int k = 0; k < burstLen && (i + k) < LOOP_SAMPLES; k++) {
                        double decay = Math.exp(-k / 12.0);
                        double click = Math.sin(2.0 * Math.PI * 2400.0 * k / SAMPLE_RATE) * decay * 1000.0;
                        raw[i + k] += click;
                    }
                }
            }
            case NATURAL_LINE -> {
                targetRms = 750.0; // ~ -32.8 dBFS

                // 50Hz AC mains hum + 100Hz harmonic + analog telephony line hiss
                double b0 = 0, b1 = 0, b2 = 0;
                for (int i = 0; i < LOOP_SAMPLES; i++) {
                    double hum = Math.sin(2.0 * Math.PI * 50.0 * i / SAMPLE_RATE) * 300.0
                            + Math.sin(2.0 * Math.PI * 100.0 * i / SAMPLE_RATE) * 120.0;
                    double white = rand.nextDouble() * 2.0 - 1.0;
                    b0 = 0.99765 * b0 + white * 0.0990460;
                    b1 = 0.96300 * b1 + white * 0.2965164;
                    b2 = 0.57000 * b2 + white * 1.0526913;
                    double pink = (b0 + b1 + b2 + white * 0.1848) * 220.0;
                    raw[i] = hum + pink;
                }
            }
            case CAFE -> {
                targetRms = 950.0; // ~ -30.8 dBFS

                // Low-mid diffuse crowd babble & room reverberation
                double b0 = 0, b1 = 0;
                for (int i = 0; i < LOOP_SAMPLES; i++) {
                    double white = rand.nextDouble() * 2.0 - 1.0;
                    b0 = 0.95 * b0 + white * 0.12;
                    b1 = 0.85 * b1 + white * 0.18;
                    double rumble = Math.sin(2.0 * Math.PI * 120.0 * i / SAMPLE_RATE) * 180.0
                            + Math.sin(2.0 * Math.PI * 220.0 * i / SAMPLE_RATE) * 140.0;
                    raw[i] = (b0 + b1) * 320.0 + rumble;
                }

                // Occasional ceramic cup/spoon chime (fast high decay ~1900 Hz)
                for (int i = 1500; i < LOOP_SAMPLES - 800; i += (int) (SAMPLE_RATE * (2.0 + rand.nextDouble() * 3.0))) {
                    int chimeLen = (int) (SAMPLE_RATE * 0.05);
                    for (int k = 0; k < chimeLen && (i + k) < LOOP_SAMPLES; k++) {
                        double decay = Math.exp(-k / 50.0);
                        double chime = Math.sin(2.0 * Math.PI * 1920.0 * k / SAMPLE_RATE) * decay * 1200.0;
                        raw[i + k] += chime;
                    }
                }
            }
            case OFF -> {
                return new short[0];
            }
        }

        // Apply RMS calibration to ensure consistent and audible loudness
        double sumSq = 0;
        for (int i = 0; i < LOOP_SAMPLES; i++) {
            sumSq += raw[i] * raw[i];
        }
        double currentRms = Math.sqrt(sumSq / LOOP_SAMPLES);
        double gain = (currentRms > 0) ? (targetRms / currentRms) : 1.0;

        short[] buffer = new short[LOOP_SAMPLES];
        for (int i = 0; i < LOOP_SAMPLES; i++) {
            int scaled = (int) Math.round(raw[i] * gain);
            buffer[i] = (short) Math.max(-32768, Math.min(32767, scaled));
        }

        // Smooth cosine cross-fade at loop boundaries (400 samples / 50ms) for seamless looping
        int fadeSamples = 400;
        for (int i = 0; i < fadeSamples; i++) {
            double ratio = 0.5 * (1.0 - Math.cos(Math.PI * i / fadeSamples));
            short startSample = buffer[i];
            short endSample = buffer[LOOP_SAMPLES - fadeSamples + i];
            buffer[i] = (short) Math.round(startSample * ratio + endSample * (1.0 - ratio));
        }

        return buffer;
    }
}
