package uz.murodjon.robotcallv2.agent.audio;

import uz.murodjon.robotcallv2.aiagent.domain.enums.NoiseCancellationMode;

/**
 * Cleans the caller's leg before the recognizer hears it: 8 kHz 16-bit mono PCM in,
 * the same frame out with what is not speech pulled down.
 *
 * <p>Three stages, in order: a 100 Hz high-pass that removes DC offset, mains hum and
 * handset rumble; a 3.4 kHz low-pass that removes hiss above the telephone passband; then
 * one broadband gain per frame, set from how far the frame sits above a noise floor the
 * filter tracks itself.
 *
 * <p>Deliberately broadband and not spectral: a per-band gate needs an FFT per frame on
 * the RTP thread, and at 8 kHz with 20 ms frames there is not enough resolution for the
 * bands to be worth that. The trade is that a frame is either mostly speech or mostly
 * noise — noise <em>under</em> speech is passed rather than removed.
 *
 * <p>The gain is smoothed with a fast attack and a slow release so a consonant is not
 * clipped by the gate opening late, and a word's tail is not chopped by it closing early.
 * Stateful across frames and therefore one instance per call.
 */
public class TelephonyNoiseCanceller {

    private static final int SAMPLE_RATE = 8000;

    /** Fast enough to be open before a plosive has finished. */
    private static final double ATTACK_ALPHA = 1.0 / (0.008 * SAMPLE_RATE);   // 8 ms
    /** Slow enough to carry a word's tail through the gap between its syllables. */
    private static final double RELEASE_ALPHA = 1.0 / (0.060 * SAMPLE_RATE);  // 60 ms

    private volatile boolean enabled = true;
    private volatile NoiseCancellationMode mode = NoiseCancellationMode.BACKGROUND_NOISE_SUPPRESSION;

    // High-pass filter state (100 Hz cutoff @ 8 kHz)
    private double hpX1 = 0, hpX2 = 0, hpY1 = 0, hpY2 = 0;
    // Low-pass filter state (3400 Hz cutoff @ 8 kHz)
    private double lpX1 = 0, lpX2 = 0, lpY1 = 0, lpY2 = 0;

    // Adaptive noise floor tracking (in RMS amplitude units)
    private double noiseFloorRms = 120.0; // initial estimate (~ -48 dBFS)
    private double smoothedGain = 1.0;

    // Biquad coefficients for 100 Hz Butterworth HPF at 8 kHz
    // f0 = 100, Q = 0.7071
    private static final double HP_B0 = 0.94597794;
    private static final double HP_B1 = -1.89195587;
    private static final double HP_B2 = 0.94597794;
    private static final double HP_A1 = -1.88903308;
    private static final double HP_A2 = 0.89487867;

    // Biquad coefficients for 3400 Hz Butterworth LPF at 8 kHz (RBJ cookbook)
    // f0 = 3400, Q = 0.7071
    private static final double LP_B0 = 0.71573741;
    private static final double LP_B1 = 1.43147482;
    private static final double LP_B2 = 0.71573741;
    private static final double LP_A1 = 1.34896775;
    private static final double LP_A2 = 0.51398189;

    public TelephonyNoiseCanceller() {
    }

    public TelephonyNoiseCanceller(boolean enabled, NoiseCancellationMode mode) {
        configure(enabled, mode);
    }

    public void configure(boolean enabled, NoiseCancellationMode mode) {
        this.enabled = enabled;
        this.mode = mode != null ? mode : NoiseCancellationMode.BACKGROUND_NOISE_SUPPRESSION;
    }

    public boolean isEnabled() {
        return enabled && mode != NoiseCancellationMode.OFF;
    }

    /**
     * Filters an incoming frame of 16-bit 8 kHz PCM samples in place or returns a cleaned buffer.
     *
     * @param pcm    input samples
     * @param length number of valid samples in the buffer
     * @return cleaned 16-bit PCM samples
     */
    public short[] process(short[] pcm, int length) {
        if (!isEnabled() || pcm == null || length <= 0) {
            return pcm;
        }

        short[] output = new short[length];
        double sumSq = 0.0;

        // Step 1: High-pass and Low-pass pre-filtering
        for (int i = 0; i < length; i++) {
            double x = pcm[i];

            // 100 Hz High-pass (kills DC offset, desk rumble, HVAC 50/60Hz hum)
            double hpOut = HP_B0 * x + HP_B1 * hpX1 + HP_B2 * hpX2 - HP_A1 * hpY1 - HP_A2 * hpY2;
            hpX2 = hpX1;
            hpX1 = x;
            hpY2 = hpY1;
            hpY1 = hpOut;

            // 3400 Hz Low-pass (kills high-frequency hiss outside telephone band)
            double lpOut = LP_B0 * hpOut + LP_B1 * lpX1 + LP_B2 * lpX2 - LP_A1 * lpY1 - LP_A2 * lpY2;
            lpX2 = lpX1;
            lpX1 = hpOut;
            lpY2 = lpY1;
            lpY1 = lpOut;

            sumSq += lpOut * lpOut;
            output[i] = (short) Math.max(-32768, Math.min(32767, Math.round(lpOut)));
        }

        double frameRms = Math.sqrt(sumSq / length);

        // Step 2: Adaptive Noise Floor Estimation
        // When signal is quiet, update noise floor estimate quickly; during loud speech, hold or drift slowly
        if (frameRms < noiseFloorRms * 1.5) {
            noiseFloorRms = 0.92 * noiseFloorRms + 0.08 * frameRms;
        } else if (frameRms < noiseFloorRms * 3.0) {
            noiseFloorRms = 0.98 * noiseFloorRms + 0.02 * frameRms;
        } else {
            // Slow upward drift during long loud speech
            noiseFloorRms = 0.999 * noiseFloorRms + 0.001 * frameRms;
        }
        noiseFloorRms = Math.max(30.0, Math.min(1500.0, noiseFloorRms));

        // Step 3: Compute SNR and Target Gain based on mode
        double snr = (frameRms - noiseFloorRms) / (noiseFloorRms + 1e-6);
        double targetGain;

        if (mode == NoiseCancellationMode.VOICE_ISOLATION) {
            // Voice Isolation: aggressively gates non-speech and isolates primary talker
            if (snr <= 0.4) {
                targetGain = 0.04; // ~ -28 dB floor
            } else if (snr < 2.0) {
                // Soft sigmoid ramp from noise floor to speech
                double t = (snr - 0.4) / 1.6;
                targetGain = 0.04 + 0.96 * Math.pow(t, 1.8);
            } else {
                targetGain = 1.0;
            }
        } else {
            // Background Noise Suppression: smooth Wiener-style attenuation
            // Preserves natural voice while suppressing ambient fans, road noise, typing
            if (snr <= 0.1) {
                targetGain = 0.12; // ~ -18 dB floor
            } else {
                double wiener = snr / (snr + 0.85);
                targetGain = 0.12 + 0.88 * Math.min(1.0, wiener);
            }
        }

        // Step 4: Attack / Release envelope smoothing to avoid speech clipping or pumping.
        // These are per-sample coefficients, so the time constant is 1/alpha samples at
        // 8 kHz — the reason they are derived rather than written as literals is that
        // plausible-looking literals here were ~30x too fast, closing the gate inside a
        // word and pumping audibly on every syllable.
        for (int i = 0; i < length; i++) {
            double alpha = (targetGain > smoothedGain) ? ATTACK_ALPHA : RELEASE_ALPHA;
            smoothedGain = (1.0 - alpha) * smoothedGain + alpha * targetGain;

            int sample = (int) Math.round(output[i] * smoothedGain);
            output[i] = (short) Math.max(-32768, Math.min(32767, sample));
        }

        return output;
    }

    /** Resets filter history between calls. */
    public void reset() {
        hpX1 = hpX2 = hpY1 = hpY2 = 0;
        lpX1 = lpX2 = lpY1 = lpY2 = 0;
        noiseFloorRms = 120.0;
        smoothedGain = 1.0;
    }
}
