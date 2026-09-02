package uz.murodjon.robotcallv2.agent.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResamplerTest {

    @Test
    void doublesTheSampleCount() {
        short[] in = {100, 200, 300};
        short[] out = Resampler.upsample8kTo16k(in, in.length);
        assertEquals(6, out.length);
    }

    @Test
    void keepsOriginalSamplesAndInterpolatesBetween() {
        short[] in = {0, 100};
        short[] out = Resampler.upsample8kTo16k(in, in.length);
        assertEquals(0, out[0]);    // original
        assertEquals(50, out[1]);   // midpoint between 0 and 100
        assertEquals(100, out[2]);  // original
        assertEquals(100, out[3]);  // last sample repeated (no next)
    }

    @Test
    void handlesEmptyInput() {
        assertEquals(0, Resampler.upsample8kTo16k(new short[0], 0).length);
        assertEquals(0, Resampler.downsample16kTo8k(new short[0], 0).length);
        assertEquals(0, Resampler.downsample24kTo8k(new short[0], 0).length);
    }

    @Test
    void respectsLengthArgument() {
        short[] in = {10, 20, 999}; // only first two are valid
        short[] out = Resampler.upsample8kTo16k(in, 2);
        assertEquals(4, out.length);
        assertEquals(10, out[0]);
        assertEquals(15, out[1]);
        assertEquals(20, out[2]);
        assertEquals(20, out[3]);
    }

    @Test
    void downsamples16kTo8kCorrectly() {
        short[] in = new short[160];
        for (int i = 0; i < in.length; i++) {
            in[i] = 1000;
        }
        short[] out = Resampler.downsample16kTo8k(in, in.length);
        assertEquals(80, out.length);
        // DC signal (1000) should remain around 1000 after low-pass filter
        for (short sample : out) {
            assertTrue(Math.abs(sample - 1000) <= 20, "Sample " + sample + " should be close to 1000");
        }
    }

    @Test
    void downsamples24kTo8kCorrectly() {
        short[] in = new short[240];
        for (int i = 0; i < in.length; i++) {
            in[i] = 2000;
        }
        short[] out = Resampler.downsample24kTo8k(in, in.length);
        assertEquals(80, out.length);
        for (short sample : out) {
            assertTrue(Math.abs(sample - 2000) <= 20, "Sample " + sample + " should be close to 2000");
        }
    }
}
