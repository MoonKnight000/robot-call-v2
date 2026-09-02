package uz.murodjon.robotcallv2.agent.audio;

import uz.murodjon.robotcallv2.campaign.domain.enums.AmbientSound;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * Generates realistic ambient background soundscapes (OFF, OFFICE, CALL_CENTER, NATURAL_LINE, CAFE)
 * at approximately -32dB (subtle background presence) in 8kHz 16-bit mono PCM.
 */
public final class AmbientSoundGenerator {

    private static final int SAMPLE_RATE = 8000;
    private static final int LOOP_DURATION_SECONDS = 5;
    private static final int LOOP_SAMPLES = SAMPLE_RATE * LOOP_DURATION_SECONDS; // 40,000 samples

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
     * @return 16-bit PCM frame with ambient audio mixed in at -32dB
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
     * Generates a realistic looping soundscape buffer.
     */
    private static short[] generateLoop(AmbientSound sound) {
        short[] buffer = new short[LOOP_SAMPLES];
        Random rand = new Random(sound.ordinal() * 31337L + 42);

        switch (sound) {
            case NATURAL_LINE -> {
                // Subtle 50Hz hum + gentle white/pink line hiss (~-34dB)
                double humFreq = 50.0;
                double b0 = 0, b1 = 0, b2 = 0;
                for (int i = 0; i < LOOP_SAMPLES; i++) {
                    double hum = Math.sin(2.0 * Math.PI * humFreq * i / SAMPLE_RATE) * 18.0;
                    double white = (rand.nextDouble() * 2.0 - 1.0);
                    // Simple low-pass filter for pinkish noise
                    b0 = 0.99765 * b0 + white * 0.0990460;
                    b1 = 0.96300 * b1 + white * 0.2965164;
                    b2 = 0.57000 * b2 + white * 1.0526913;
                    double pink = b0 + b1 + b2 + white * 0.1848;
                    double sample = hum + pink * 12.0;
                    buffer[i] = (short) Math.max(-32768, Math.min(32767, (int) sample));
                }
            }
            case OFFICE -> {
                // Subtle room presence + occasional soft keyboard tap/paper rustle
                double b0 = 0, b1 = 0;
                for (int i = 0; i < LOOP_SAMPLES; i++) {
                    double white = (rand.nextDouble() * 2.0 - 1.0);
                    b0 = 0.95 * b0 + white * 0.1;
                    b1 = 0.85 * b1 + white * 0.15;
                    double baseNoise = (b0 + b1) * 10.0;

                    // Occasional soft click / tap (probability ~0.002 per sample)
                    double click = 0;
                    if (rand.nextDouble() < 0.0008) {
                        click = (rand.nextDouble() * 2.0 - 1.0) * 80.0;
                    }
                    double sample = baseNoise + click;
                    buffer[i] = (short) Math.max(-32768, Math.min(32767, (int) sample));
                }
            }
            case CALL_CENTER -> {
                // Distant multi-speaker murmur (filtered multi-frequency harmonics + gentle chatter buzz)
                double[] speakerFreqs = {160.0, 220.0, 310.0, 480.0, 620.0};
                for (int i = 0; i < LOOP_SAMPLES; i++) {
                    double murmur = 0;
                    for (int s = 0; s < speakerFreqs.length; s++) {
                        double modulation = 0.5 + 0.5 * Math.sin(2.0 * Math.PI * (0.2 + s * 0.15) * i / SAMPLE_RATE);
                        murmur += Math.sin(2.0 * Math.PI * speakerFreqs[s] * i / SAMPLE_RATE) * 8.0 * modulation;
                    }
                    double white = (rand.nextDouble() * 2.0 - 1.0) * 14.0;
                    double sample = murmur + white;
                    buffer[i] = (short) Math.max(-32768, Math.min(32767, (int) sample));
                }
            }
            case CAFE -> {
                // Low-frequency room reverberation + occasional ceramic chime/clink
                for (int i = 0; i < LOOP_SAMPLES; i++) {
                    double white = (rand.nextDouble() * 2.0 - 1.0) * 16.0;
                    // Occasional ceramic clink (high pitch fast decay, e.g. 1800 Hz)
                    double clink = 0;
                    if (rand.nextDouble() < 0.0004) {
                        for (int k = 0; k < 120 && (i + k) < LOOP_SAMPLES; k++) {
                            double decay = Math.exp(-k / 20.0);
                            double chime = Math.sin(2.0 * Math.PI * 1850.0 * k / SAMPLE_RATE) * 90.0 * decay;
                            buffer[i + k] = (short) Math.max(-32768, Math.min(32767, buffer[i + k] + (int) chime));
                        }
                    }
                    buffer[i] = (short) Math.max(-32768, Math.min(32767, buffer[i] + (int) white));
                }
            }
            case OFF -> {
                // Silence
            }
        }

        // Apply smooth cross-fade at boundaries (first/last 200 samples) for seamless loop
        int fadeSamples = 200;
        for (int i = 0; i < fadeSamples; i++) {
            double ratio = (double) i / fadeSamples;
            buffer[i] = (short) (buffer[i] * ratio + buffer[LOOP_SAMPLES - fadeSamples + i] * (1.0 - ratio));
        }

        return buffer;
    }
}
