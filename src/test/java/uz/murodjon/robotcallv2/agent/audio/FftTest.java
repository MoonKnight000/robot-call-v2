package uz.murodjon.robotcallv2.agent.audio;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FftTest {

    @Test
    void aPureToneLandsInItsOwnBin() {
        Fft fft = new Fft(512);
        double[] re = new double[512];
        double[] im = new double[512];
        for (int i = 0; i < 512; i++) {
            re[i] = Math.cos(2 * Math.PI * 10 * i / 512);
        }
        fft.forward(re, im);

        assertThat(re[10]).isCloseTo(256, within(1e-6));
        assertThat(re[502]).isCloseTo(256, within(1e-6));
        assertThat(Math.hypot(re[11], im[11])).isLessThan(1e-6);
    }

    @Test
    void inverseUndoesForward() {
        Fft fft = new Fft(512);
        Random random = new Random(3);
        double[] original = new double[512];
        double[] re = new double[512];
        double[] im = new double[512];
        for (int i = 0; i < 512; i++) {
            original[i] = random.nextGaussian();
            re[i] = original[i];
        }
        fft.forward(re, im);
        fft.inverse(re, im);

        for (int i = 0; i < 512; i++) {
            assertThat(re[i]).isCloseTo(original[i], within(1e-9));
            assertThat(im[i]).isCloseTo(0, within(1e-9));
        }
    }
}
