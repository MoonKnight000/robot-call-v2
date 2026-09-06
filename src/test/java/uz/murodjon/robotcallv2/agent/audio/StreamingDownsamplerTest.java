package uz.murodjon.robotcallv2.agent.audio;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class StreamingDownsamplerTest {

    @Test
    void chunkedOutputMatchesOneShotResampling24k() {
        short[] signal = noise(24000 * 2, 1);
        short[] whole = Resampler.downsample24kTo8k(signal, signal.length);

        StreamingDownsampler streaming = StreamingDownsampler.from24kTo8k();
        short[] chunked = new short[0];
        // Chunk sizes that are not multiples of three, like a synthesizer's SSE chunks.
        for (int off = 0, n = 0; off < signal.length; off += n) {
            n = Math.min(signal.length - off, 700 + (off / 700) % 5);
            chunked = concat(chunked, streaming.push(Arrays.copyOfRange(signal, off, off + n), n));
        }
        chunked = concat(chunked, streaming.flush());

        assertThat(chunked.length).isEqualTo(whole.length);
        assertThat(Arrays.copyOf(chunked, whole.length - 8)).isEqualTo(Arrays.copyOf(whole, whole.length - 8));
    }

    @Test
    void chunkedOutputMatchesOneShotResampling16k() {
        short[] signal = noise(16000, 2);
        short[] whole = Resampler.downsample16kTo8k(signal, signal.length);

        StreamingDownsampler streaming = StreamingDownsampler.from16kTo8k();
        short[] chunked = new short[0];
        for (int off = 0; off < signal.length; off += 333) {
            int n = Math.min(333, signal.length - off);
            chunked = concat(chunked, streaming.push(Arrays.copyOfRange(signal, off, off + n), n));
        }
        chunked = concat(chunked, streaming.flush());

        assertThat(chunked.length).isEqualTo(whole.length);
        assertThat(Arrays.copyOf(chunked, whole.length - 8)).isEqualTo(Arrays.copyOf(whole, whole.length - 8));
    }

    private static short[] noise(int samples, long seed) {
        Random random = new Random(seed);
        short[] out = new short[samples];
        for (int i = 0; i < samples; i++) {
            out[i] = (short) (random.nextInt(20000) - 10000);
        }
        return out;
    }

    private static short[] concat(short[] a, short[] b) {
        short[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
