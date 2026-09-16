package uz.murodjon.robotcallv2.agent.audio;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StreamingUpsamplerTest {

    private static final int FRAME = 160;

    @Test
    void steadyStateFramesDoubleInLength() {
        StreamingUpsampler up = StreamingUpsampler.from8kTo16k();
        up.push(new short[FRAME], FRAME);
        assertThat(up.push(new short[FRAME], FRAME)).hasSize(FRAME * 2);
        assertThat(StreamingUpsampler.from8kTo24k().push(new short[FRAME], FRAME).length)
                .isLessThan(FRAME * 3);
    }

    @Test
    void originalSamplesPassThroughUnchanged() {
        short[] in = new short[FRAME];
        for (int i = 0; i < FRAME; i++) {
            in[i] = (short) (i * 37 - 3000);
        }
        short[] out = StreamingUpsampler.from8kTo16k().push(in, FRAME);
        for (int k = 0; k * 2 < out.length; k++) {
            assertThat(out[k * 2]).isEqualTo(in[k]);
        }
    }

    @Test
    void interpolatesAToneAccurately() {
        double amplitude = 10000;
        double frequency = 1000;
        StreamingUpsampler up = StreamingUpsampler.from8kTo16k();
        List<Short> out = new ArrayList<>();
        for (int frame = 0; frame < 20; frame++) {
            short[] in = new short[FRAME];
            for (int i = 0; i < FRAME; i++) {
                int n = frame * FRAME + i;
                in[i] = (short) Math.round(amplitude * Math.sin(2 * Math.PI * frequency * n / 8000.0));
            }
            for (short s : up.push(in, FRAME)) {
                out.add(s);
            }
        }
        // Output sample m sits at 16 kHz time m; skip the start, where the filter still
        // sees the silence before the stream began.
        for (int m = 200; m < out.size(); m++) {
            double expected = amplitude * Math.sin(2 * Math.PI * frequency * m / 16000.0);
            assertThat((double) out.get(m)).isCloseTo(expected, org.assertj.core.data.Offset.offset(amplitude * 0.01));
        }
    }

    @Test
    void telephoneRatePassesThroughAndUnknownRatesAreRefused() {
        assertThat(StreamingUpsampler.from8kTo(8000)).isNull();
        assertThatThrownBy(() -> StreamingUpsampler.from8kTo(44100)).isInstanceOf(IllegalArgumentException.class);
    }
}
