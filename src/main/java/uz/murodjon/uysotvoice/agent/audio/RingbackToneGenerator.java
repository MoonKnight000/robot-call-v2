package uz.murodjon.uysotvoice.agent.audio;

/**
 * Generates realistic telephony ringback tones (gudok) and soft connection chimes
 * in 8kHz 16-bit mono PCM.
 */
public final class RingbackToneGenerator {

    private static final int SAMPLE_RATE = 8000;

    private RingbackToneGenerator() {}

    /**
     * Generates a realistic European/Uzbekistan style ringback tone (gudok):
     * 425 Hz tone for 1.0 second + 0.5s pause, repeated for the requested total duration.
     *
     * @param totalDurationMs total duration in milliseconds
     * @return 8kHz 16-bit PCM samples
     */
    public static short[] generateRingback(int totalDurationMs) {
        int totalSamples = (SAMPLE_RATE * totalDurationMs) / 1000;
        short[] samples = new short[totalSamples];

        double freq = 425.0; // Standard European/CIS 425 Hz ringback tone
        int toneSamples = (SAMPLE_RATE * 1000) / 1000; // 1 second tone
        int periodSamples = (SAMPLE_RATE * 1500) / 1000; // 1s tone + 0.5s pause

        for (int i = 0; i < totalSamples; i++) {
            int posInPeriod = i % periodSamples;
            if (posInPeriod < toneSamples) {
                // Apply soft envelope fade-in/fade-out to avoid clicks
                double envelope = 1.0;
                if (posInPeriod < 160) { // 20ms fade in
                    envelope = posInPeriod / 160.0;
                } else if (posInPeriod > toneSamples - 160) { // 20ms fade out
                    envelope = (toneSamples - posInPeriod) / 160.0;
                }
                double sin = Math.sin(2.0 * Math.PI * freq * i / SAMPLE_RATE);
                samples[i] = (short) (sin * 6000 * envelope); // Gentle audible volume
            } else {
                samples[i] = 0;
            }
        }
        return samples;
    }

    /**
     * Generates a pleasant Telegram/VoIP-style double connection chime (soft intro tone).
     */
    public static short[] generateConnectionChime() {
        int durationMs = 600;
        int totalSamples = (SAMPLE_RATE * durationMs) / 1000;
        short[] samples = new short[totalSamples];

        // Tone 1: 523.25 Hz (C5), Tone 2: 659.25 Hz (E5)
        int half = totalSamples / 2;
        for (int i = 0; i < totalSamples; i++) {
            double freq = (i < half) ? 523.25 : 659.25;
            int offset = (i < half) ? i : (i - half);
            double envelope = Math.exp(-offset / (SAMPLE_RATE * 0.15)); // Soft decay
            double sin = Math.sin(2.0 * Math.PI * freq * i / SAMPLE_RATE);
            samples[i] = (short) (sin * 5000 * envelope);
        }
        return samples;
    }
}
