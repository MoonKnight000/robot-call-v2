package uz.murodjon.uysotvoice.agent.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
