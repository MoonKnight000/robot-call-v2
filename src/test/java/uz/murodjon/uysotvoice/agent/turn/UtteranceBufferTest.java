package uz.murodjon.uysotvoice.agent.turn;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the detector is handed has to be the END of the utterance, in order, at the rate
 * the model was trained on. A ring that wraps the wrong way would score the caller's
 * words shuffled and still return a plausible-looking probability.
 */
class UtteranceBufferTest {

    /** One second of 16 kHz — so 8000 samples of the 8 kHz line are held. */
    private static final int SAMPLES_16K = 16000;

    private static short[] ramp(int from, int count) {
        short[] pcm = new short[count];
        for (int i = 0; i < count; i++) {
            pcm[i] = (short) (from + i);
        }
        return pcm;
    }

    @Test
    void startsEmpty() {
        assertThat(new UtteranceBuffer(SAMPLES_16K).length()).isZero();
    }

    @Test
    void reportsTwiceWhatItHolds() {
        // The line runs at 8 kHz and the model at 16, so every held sample is two.
        UtteranceBuffer buffer = new UtteranceBuffer(SAMPLES_16K);

        buffer.onAudio(ramp(0, 160), 160);

        assertThat(buffer.length()).isEqualTo(320);
        assertThat(buffer.recent()).hasSize(320);
    }

    @Test
    void keepsTheAudioInOrder() {
        UtteranceBuffer buffer = new UtteranceBuffer(SAMPLES_16K);
        buffer.onAudio(ramp(100, 4), 4);
        buffer.onAudio(ramp(200, 4), 4);

        short[] recent = buffer.recent();

        // Upsampling doubles each sample's position, so the oldest is still first and the
        // newest still last whatever the interpolation does in between.
        assertThat(recent[0]).isEqualTo((short) 100);
        assertThat(recent).hasSize(16);
    }

    @Test
    void dropsTheOldestOnceItIsFull() {
        // 200 samples of 16 kHz means 100 of the line, so the first 100 fall out.
        UtteranceBuffer buffer = new UtteranceBuffer(200);
        buffer.onAudio(ramp(0, 100), 100);
        buffer.onAudio(ramp(1000, 100), 100);

        short[] recent = buffer.recent();

        assertThat(recent).hasSize(200);
        assertThat(recent[0]).isEqualTo((short) 1000);
    }

    @Test
    void aFrameLargerThanTheRingKeepsOnlyItsTail() {
        UtteranceBuffer buffer = new UtteranceBuffer(20);   // 10 line samples

        buffer.onAudio(ramp(0, 100), 100);

        assertThat(buffer.length()).isEqualTo(20);
        assertThat(buffer.recent()[0]).isEqualTo((short) 90);
    }

    @Test
    void readingDoesNotEmptyIt() {
        // The same audio is still the run-up to whatever the caller says next.
        UtteranceBuffer buffer = new UtteranceBuffer(SAMPLES_16K);
        buffer.onAudio(ramp(0, 160), 160);

        assertThat(buffer.recent()).hasSize(320);
        assertThat(buffer.recent()).hasSize(320);
    }
}
