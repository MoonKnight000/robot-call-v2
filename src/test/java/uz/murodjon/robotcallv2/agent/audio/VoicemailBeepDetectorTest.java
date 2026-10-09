package uz.murodjon.robotcallv2.agent.audio;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The message must start on the beep and not inside the greeting: a greeting mistaken for
 * a beep puts the message over the part of it the recorder never keeps.
 */
class VoicemailBeepDetectorTest {

    private static final int RATE = 8000;
    /** Not a multiple of the detector's 20 ms frame, so frames are assembled across calls. */
    private static final int CHUNK = 256;
    private static final int SILENCE_MS = 1500;
    private static final int MAX_WAIT_MS = 10_000;

    private final AtomicInteger readies = new AtomicInteger();
    private final Random random = new Random(42);

    private VoicemailBeepDetector detector() {
        return new VoicemailBeepDetector("ch-1", RATE, SILENCE_MS, MAX_WAIT_MS, readies::incrementAndGet);
    }

    @Test
    void startsOnTheBeepAfterAGreeting() {
        VoicemailBeepDetector d = detector();

        feed(d, voice(3000));
        assertThat(readies.get()).isZero();

        feed(d, tone(1000, 400));
        assertThat(readies.get()).isEqualTo(1);
    }

    @Test
    void hearsABeepThatFallsBetweenTwoBins() {
        VoicemailBeepDetector d = detector();

        feed(d, tone(1012.5, 400));

        assertThat(readies.get()).isEqualTo(1);
    }

    @Test
    void doesNotTakeAVoicedGreetingForABeep() {
        VoicemailBeepDetector d = detector();

        feed(d, voice(8000));

        assertThat(readies.get()).isZero();
    }

    @Test
    void doesNotTakeABlipForABeep() {
        VoicemailBeepDetector d = detector();

        feed(d, noise(1000));
        feed(d, tone(1000, 80));
        feed(d, noise(1000));

        assertThat(readies.get()).isZero();
    }

    @Test
    void startsWhenTheBoxGoesQuietWithoutABeep() {
        VoicemailBeepDetector d = detector();

        feed(d, voice(2000));
        feed(d, silence(SILENCE_MS - 200));
        assertThat(readies.get()).isZero();

        feed(d, silence(400));
        assertThat(readies.get()).isEqualTo(1);
    }

    @Test
    void startsAnywayOnceTheWaitRunsOut() {
        VoicemailBeepDetector d = detector();

        feed(d, noise(MAX_WAIT_MS - 200));
        assertThat(readies.get()).isZero();

        feed(d, noise(400));
        assertThat(readies.get()).isEqualTo(1);
    }

    @Test
    void firesOnlyOnce() {
        VoicemailBeepDetector d = detector();

        feed(d, tone(1000, 400));
        feed(d, silence(SILENCE_MS * 2));
        feed(d, noise(MAX_WAIT_MS));

        assertThat(readies.get()).isEqualTo(1);
    }

    private static void feed(VoicemailBeepDetector d, short[] pcm) {
        short[] chunk = new short[CHUNK];
        for (int offset = 0; offset < pcm.length; offset += CHUNK) {
            int length = Math.min(CHUNK, pcm.length - offset);
            System.arraycopy(pcm, offset, chunk, 0, length);
            d.onAudio(chunk, length);
        }
    }

    private static short[] tone(double hz, int ms) {
        short[] pcm = new short[ms * RATE / 1000];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (short) (8000 * Math.sin(2 * Math.PI * hz * i / RATE));
        }
        return pcm;
    }

    /** A voiced sound: a gliding 120–180 Hz fundamental and its harmonics, the way a vowel spreads its energy. */
    private static short[] voice(int ms) {
        short[] pcm = new short[ms * RATE / 1000];
        double phase = 0;
        for (int i = 0; i < pcm.length; i++) {
            double f0 = 150 + 30 * Math.sin(2 * Math.PI * 0.7 * i / RATE);
            phase += 2 * Math.PI * f0 / RATE;
            double sample = 0;
            for (int k = 1; k <= 20; k++) {
                sample += Math.sin(k * phase) / k;
            }
            pcm[i] = (short) (4000 * sample);
        }
        return pcm;
    }

    private short[] noise(int ms) {
        short[] pcm = new short[ms * RATE / 1000];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (short) (random.nextGaussian() * 2000);
        }
        return pcm;
    }

    private static short[] silence(int ms) {
        return new short[ms * RATE / 1000];
    }
}
