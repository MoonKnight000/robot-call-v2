package uz.murodjon.robotcallv2.agent.audio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpeechLevelNormalizerTest {

    private static final int CHUNK = 320;

    private static short[] tone(double amplitude) {
        short[] pcm = new short[CHUNK];
        for (int i = 0; i < CHUNK; i++) {
            pcm[i] = (short) Math.round(amplitude * Math.sin(2 * Math.PI * 440 * i / 8000.0));
        }
        return pcm;
    }

    private static double rms(short[] pcm) {
        double sum = 0;
        for (short s : pcm) {
            sum += (double) s * s;
        }
        return Math.sqrt(sum / pcm.length);
    }

    @Test
    void quietSpeechIsBroughtUp() {
        SpeechLevelNormalizer normalizer = new SpeechLevelNormalizer();
        short[] last = null;
        for (int i = 0; i < 40; i++) {
            last = normalizer.process(tone(1131), CHUNK); // about 800 RMS
        }
        assertThat(rms(last)).isBetween(2000.0, 3400.0);
    }

    @Test
    void loudSpeechIsBroughtDownWithoutClipping() {
        SpeechLevelNormalizer normalizer = new SpeechLevelNormalizer();
        short[] last = null;
        for (int i = 0; i < 40; i++) {
            last = normalizer.process(tone(30000), CHUNK);
        }
        assertThat(rms(last)).isLessThan(10000.0);
    }

    @Test
    void fullScaleIsBentUnderTheCeiling() {
        short[] pcm = new short[CHUNK];
        java.util.Arrays.fill(pcm, Short.MAX_VALUE);
        new SpeechLevelNormalizer().process(pcm, CHUNK);
        for (short s : pcm) {
            assertThat((int) s).isLessThan(32000);
        }
    }

    @Test
    void silenceStaysSilent() {
        short[] pcm = new short[CHUNK];
        new SpeechLevelNormalizer().process(pcm, CHUNK);
        assertThat(rms(pcm)).isZero();
    }
}
