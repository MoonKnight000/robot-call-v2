package uz.murodjon.robotcallv2.agent.queue;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HoldMusicGeneratorTest {

    @Test
    void sixteenSecondsAtTelephoneRate() {
        assertThat(HoldMusicGenerator.melody()).hasSize(16 * HoldMusicGenerator.SAMPLE_RATE);
    }

    @Test
    void audibleButWellUnderSpeechAndNeverClipped() {
        short[] melody = HoldMusicGenerator.generate();
        int peak = 0;
        for (short sample : melody) {
            peak = Math.max(peak, Math.abs(sample));
        }
        assertThat(peak).isBetween(1_500, 6_000);
    }

    @Test
    void generatedOnceAndShared() {
        assertThat(HoldMusicGenerator.melody()).isSameAs(HoldMusicGenerator.melody());
    }
}
