package uz.murodjon.robotcallv2.agent.audio;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A false positive here cuts off a real person mid-greeting, which is worse than the cost
 * the detector saves — so the "talkative human" case matters at least as much as the
 * voicemail one.
 */
class AnsweringMachineDetectorTest {

    private static final int RATE = 8000;
    /** 32 ms at 8 kHz — Silero's window, which is what the detector is really fed. */
    private static final int WINDOW = 256;

    private final AtomicInteger detections = new AtomicInteger();

    private AnsweringMachineDetector detector() {
        return new AnsweringMachineDetector("ch-1", RATE, 6000, 3500, 250,
                detections::incrementAndGet);
    }

    /** Feed {@code ms} of speech or silence in 32 ms windows. */
    private static void feed(AnsweringMachineDetector d, boolean speech, int ms) {
        int windows = ms * RATE / 1000 / WINDOW;
        for (int i = 0; i < windows; i++) {
            d.onVadWindow(speech, WINDOW);
        }
    }

    @Test
    void detectsALongUninterruptedGreeting() {
        AnsweringMachineDetector d = detector();

        feed(d, true, 4000);

        assertThat(detections.get()).isEqualTo(1);
        assertThat(d.isFinished()).isTrue();
    }

    @Test
    void leavesAShortHumanAnswerAlone() {
        AnsweringMachineDetector d = detector();

        // "Alo?" then waiting — what a person does.
        feed(d, true, 600);
        feed(d, false, 1500);
        feed(d, true, 800);

        assertThat(detections.get()).isZero();
    }

    @Test
    void leavesATalkativeHumanAlone() {
        AnsweringMachineDetector d = detector();

        // "Assalomu alaykum, eshitaman, kim gaplashadi?" — long, but with real pauses.
        feed(d, true, 1800);
        feed(d, false, 400);
        feed(d, true, 1800);
        feed(d, false, 400);
        feed(d, true, 1200);

        assertThat(detections.get()).isZero();
    }

    @Test
    void ignoresBriefDipsInsideOneRun() {
        // Speech drops below the VAD threshold on plosives. Without the tolerance every
        // consonant would reset the run and nothing would ever be detected.
        AnsweringMachineDetector d = detector();

        for (int i = 0; i < 6; i++) {
            feed(d, true, 700);
            feed(d, false, 100); // shorter than the 250 ms tolerance
        }

        assertThat(detections.get()).isEqualTo(1);
    }

    @Test
    void stopsLookingOnceTheWindowCloses() {
        AnsweringMachineDetector d = detector();

        // A quiet first six seconds, then a long monologue — by then the conversation has
        // started and a long utterance is just someone talking.
        feed(d, false, 6100);
        assertThat(d.isFinished()).isTrue();

        feed(d, true, 5000);
        assertThat(detections.get()).isZero();
    }

    @Test
    void firesAtMostOnce() {
        AnsweringMachineDetector d = detector();

        feed(d, true, 5500);

        assertThat(detections.get()).isEqualTo(1);
    }
}
