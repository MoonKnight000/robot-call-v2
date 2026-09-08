package uz.murodjon.robotcallv2.agent.audio;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.aiagent.domain.enums.NoiseCancellationMode;

import static org.assertj.core.api.Assertions.assertThat;

class TelephonyNoiseCancellerTest {

    @Test
    void suppressesSteadyLowFrequencyRumbleAndHiss() {
        TelephonyNoiseCanceller canceller = new TelephonyNoiseCanceller(true, NoiseCancellationMode.BACKGROUND_NOISE_SUPPRESSION);

        // 160 samples (20 ms @ 8 kHz) of 50 Hz rumble + noise
        short[] noisyInput = new short[160];
        for (int i = 0; i < noisyInput.length; i++) {
            double rumble = Math.sin(2 * Math.PI * 50 * i / 8000.0) * 2000.0;
            noisyInput[i] = (short) rumble;
        }

        short[] cleaned = canceller.process(noisyInput, noisyInput.length);
        assertThat(cleaned).isNotNull();
        assertThat(cleaned.length).isEqualTo(160);

        // Compute RMS of cleaned output vs input
        double inRms = rms(noisyInput);
        double outRms = rms(cleaned);
        // High-pass filter should attenuate 50 Hz rumble substantially
        assertThat(outRms).isLessThan(inRms * 0.4);
    }

    @Test
    void voiceIsolationPreservesSpeechBandSignal() {
        TelephonyNoiseCanceller canceller = new TelephonyNoiseCanceller(true, NoiseCancellationMode.VOICE_ISOLATION);

        // Speech formant tone: 1000 Hz tone at speech level
        short[] speechInput = new short[160];
        for (int i = 0; i < speechInput.length; i++) {
            speechInput[i] = (short) (Math.sin(2 * Math.PI * 1000 * i / 8000.0) * 12000.0);
        }

        // Process a few frames to let envelope adapt
        short[] cleaned = speechInput;
        for (int k = 0; k < 5; k++) {
            cleaned = canceller.process(speechInput, speechInput.length);
        }

        double outRms = rms(cleaned);
        // Speech band tone is preserved
        assertThat(outRms).isGreaterThan(5000.0);
    }

    @Test
    void disabledPassesThroughDirectly() {
        TelephonyNoiseCanceller canceller = new TelephonyNoiseCanceller(false, NoiseCancellationMode.OFF);
        short[] input = new short[]{100, 200, 300};
        short[] output = canceller.process(input, input.length);
        assertThat(output).isSameAs(input);
    }

    private double rms(short[] pcm) {
        double sum = 0;
        for (short s : pcm) {
            sum += s * s;
        }
        return Math.sqrt(sum / pcm.length);
    }
}
