package uz.murodjon.robotcallv2.agent.turn;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The personas are only a regression net if each one is the condition it claims to be;
 * these check the audio arithmetic, not the turn-taking.
 */
class CallerPersonaAudioToolTest {

    private static final int RATE = 8000;

    @Test
    void hesitationInsertsThePauseAtTheQuietPointNearTheMiddle() {
        short[] clip = tone(1600);
        // A quiet 20 ms at 60 %: the natural place for the caller to stop and think.
        for (int i = 960; i < 1120; i++) {
            clip[i] = 0;
        }

        short[] split = CallerPersonaAudioTool.hesitate(List.of(clip), RATE, 700).get(0);

        assertThat(split).hasSize(1600 + 5600);
        assertThat(silentRun(split)).isGreaterThanOrEqualTo(5600);
    }

    @Test
    void noiseSitsAtTheRequestedDistanceBelowTheSpeech() {
        short[] speech = tone(RATE);
        short[] noisy = CallerPersonaAudioTool.addNoise(speech, 10, new Random(1));

        double noise = 0;
        for (int i = 0; i < speech.length; i++) {
            double diff = noisy[i] - speech[i];
            noise += diff * diff;
        }
        double noiseRms = Math.sqrt(noise / speech.length);
        double snr = 20 * Math.log10(CallerPersonaAudioTool.speechRms(speech) / noiseRms);

        assertThat(snr).isCloseTo(10, within(0.5));
    }

    @Test
    void theBotsLeakIsOnlyInTheGaps() {
        short[] utterance = tone(800);
        short[] leak = CallerPersonaAudioTool.scale(tone(800), -25);

        short[] call = CallerPersonaAudioTool.concat(List.of(utterance, utterance), 1000, leak);

        assertThat(call).hasSize(2 * (800 + 1000));
        assertThat(call[0]).isEqualTo(utterance[0]);
        assertThat(call[800 + 100]).isEqualTo(leak[100]);
        assertThat(call[800 + 900]).isZero(); // past the leak's own length
    }

    private static short[] tone(int samples) {
        short[] pcm = new short[samples];
        for (int i = 0; i < samples; i++) {
            pcm[i] = (short) (8000 * Math.sin(2 * Math.PI * 440 * i / RATE));
        }
        return pcm;
    }

    private static int silentRun(short[] pcm) {
        int best = 0;
        int run = 0;
        for (short sample : pcm) {
            run = sample == 0 ? run + 1 : 0;
            best = Math.max(best, run);
        }
        return best;
    }
}
