package uz.murodjon.robotcallv2.agent.audio;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Everything around the model, with the model replaced by the identity: if the STFT,
 * window, overlap-add, resampling and FIFO are right, what comes out is what went in, a
 * little later and at the same level. Any of them wrong and the recognizer would hear
 * clicks, a level drop or a frame-rate buzz with no model to blame.
 */
class NeuralDenoiseStreamTest {

    private static final int FRAME = 160;

    /** A "model" that hands every frame back untouched, and fails when told to. */
    private static NeuralDenoiseStream identityStream(boolean failing) {
        NeuralDenoiser identity = new NeuralDenoiser(new DenoiserProperties(true, ""), new SimpleMeterRegistry()) {
            @Override
            float[] enhance(float[] frame, float[][] caches) throws ai.onnxruntime.OrtException {
                if (failing) {
                    throw new ai.onnxruntime.OrtException("boom");
                }
                return frame;
            }
        };
        return new NeuralDenoiseStream(identity, new float[0][]);
    }

    private static short[] tone(int frameIndex) {
        short[] pcm = new short[FRAME];
        for (int i = 0; i < FRAME; i++) {
            int t = frameIndex * FRAME + i;
            pcm[i] = (short) Math.round(8000 * Math.sin(2 * Math.PI * 440 * t / 8000.0));
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
    void anIdentityModelPassesTheSignalThroughAtTheSameLevel() {
        NeuralDenoiseStream stream = identityStream(false);
        double inRms = rms(tone(0));
        double outRms = 0;
        for (int f = 0; f < 100; f++) {
            short[] out = stream.process(tone(f), FRAME);
            assertThat(out).hasSize(FRAME);
            if (f >= 50) {
                outRms += rms(out) / 50;
            }
        }
        assertThat(outRms).isCloseTo(inRms, within(inRms * 0.05));
    }

    @Test
    void everyFrameInGivesExactlyOneFrameOut() {
        NeuralDenoiseStream stream = identityStream(false);
        for (int f = 0; f < 37; f++) {
            assertThat(stream.process(tone(f), FRAME)).hasSize(FRAME);
        }
    }

    @Test
    void aFailingModelHandsTheCallBackToTheFallback() {
        NeuralDenoiseStream stream = identityStream(true);

        // One 20 ms frame is 320 samples at 16 kHz — enough for the first 256-sample hop.
        assertThat(stream.process(tone(0), FRAME)).isNull();
        assertThat(stream.process(tone(1), FRAME)).isNull();
    }
}
