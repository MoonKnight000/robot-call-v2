package uz.murodjon.uysotvoice.agent.turn;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * The features are the one part of the turn detector that can be wrong without anything
 * saying so: the model takes any tensor of the right shape and returns a number either
 * way. There is no Python here to compare against, so what these pin down is the
 * arithmetic that would be wrong if the mel scale, the window or the FFT were —
 * <em>where</em> a tone lands, and the normalizations Whisper applies afterwards.
 *
 * <p>They do not prove the features match {@code WhisperFeatureExtractor} bit for bit.
 * Only running the real model does, which is what the startup shape check and a first
 * ru-RU call are for.
 */
class WhisperFeaturesTest {

    private static final int RATE = 16000;
    private static final int MELS = 128;
    private static final int FRAMES = 500;

    private static WhisperFeatures features() {
        return new WhisperFeatures(RATE, 512, 256, MELS, FRAMES);
    }

    /** A steady tone, filling the whole window the extractor consumes. */
    private static short[] tone(int hz, int samples) {
        short[] pcm = new short[samples];
        for (int i = 0; i < samples; i++) {
            pcm[i] = (short) (0.5 * Short.MAX_VALUE * Math.sin(2 * Math.PI * hz * i / RATE));
        }
        return pcm;
    }

    /** Which mel band holds the most energy in a frame well away from the padded edges. */
    private static int loudestBand(float[] mel, int frame) {
        int best = 0;
        for (int band = 1; band < MELS; band++) {
            if (mel[band * FRAMES + frame] > mel[best * FRAMES + frame]) {
                best = band;
            }
        }
        return best;
    }

    @Test
    void producesTheShapeTheModelDeclares() {
        WhisperFeatures features = features();

        assertThat(features.samples()).isEqualTo(128000);   // 8 seconds at 16 kHz
        assertThat(features.extract(tone(1000, 128000), 128000)).hasSize(MELS * FRAMES);
    }

    @Test
    void anFftSizeThatIsNotAPowerOfTwoIsRefused() {
        // Whisper's own 400 is the obvious value to try, and the radix-2 transform here
        // cannot do it. Better to say so than to return a spectrogram of something else.
        assertThatThrownBy(() -> new WhisperFeatures(RATE, 400, 160, 80, 800))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("power of two");
    }

    @Test
    void aToneLandsInTheBandTheMelScalePutsIt() {
        // 1 kHz is where the Slaney scale switches from linear to logarithmic, so it is
        // also where an HTK filterbank — the other common convention — would disagree.
        int band = loudestBand(features().extract(tone(1000, 128000), 128000), FRAMES / 2);

        assertThat(band).isBetween(38, 47);
    }

    @Test
    void aLowerToneLandsInALowerBand() {
        float[] low = features().extract(tone(300, 128000), 128000);
        float[] high = features().extract(tone(3000, 128000), 128000);

        assertThat(loudestBand(low, FRAMES / 2)).isLessThan(loudestBand(high, FRAMES / 2));
    }

    @Test
    void silenceIsFlatAtWhispersFloor() {
        // log10(1e-10) = -10 everywhere, and nothing is more than 8 decades below it, so
        // the normalization leaves every bin at (-10 + 4) / 4.
        float[] mel = features().extract(new short[128000], 128000);

        for (float value : mel) {
            assertThat(value).isCloseTo(-1.5f, within(1e-5f));
        }
    }

    @Test
    void nothingSurvivesMoreThanEightDecadesBelowTheLoudestBin() {
        // Whisper's dynamic-range clamp, and the reason the features are bounded: after
        // it, the whole spectrogram spans exactly 8 decades scaled by a quarter.
        float[] mel = features().extract(tone(1000, 128000), 128000);
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (float value : mel) {
            assertThat(Float.isFinite(value)).isTrue();
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        assertThat(max - min).isLessThanOrEqualTo(2.0f + 1e-5f);
    }

    @Test
    void shorterAudioIsPaddedWithSilenceOnTheRight() {
        // The utterance ends where the caller stopped; what follows it is silence, and
        // the buffer is rarely full when a short answer is scored.
        short[] half = tone(1000, 64000);

        float[] mel = features().extract(half, half.length);

        assertThat(mel).hasSize(MELS * FRAMES);
        // The first half carries the tone, the padded tail is at the floor.
        assertThat(mel[42 * FRAMES + 100]).isGreaterThan(mel[42 * FRAMES + FRAMES - 5]);
    }

    @Test
    void theScratchBuffersDoNotLeakBetweenCalls() {
        WhisperFeatures features = features();
        short[] pcm = tone(1000, 128000);

        assertThat(features.extract(pcm, pcm.length))
                .isEqualTo(features.extract(pcm, pcm.length));
    }
}
