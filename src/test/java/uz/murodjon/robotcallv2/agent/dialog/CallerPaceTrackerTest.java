package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The bot meets the caller's pace — by a few percent at most, and only once two utterances agree. */
class CallerPaceTrackerTest {

    private static final CallerPaceProperties ON = new CallerPaceProperties(true, 2.5, 6, 1.3);

    private static DialogSession session() {
        return DialogSession.builder("chan-pace", "uz-UZ").build();
    }

    /** {@code words} words in {@code ms} of speech. */
    private static String words(int words) {
        return "so'z ".repeat(words).strip();
    }

    @Test
    void aFastCallerSpeedsTheBotUpWithinTheBoundAndShortensItsReplies() {
        DialogSession s = session();
        CallerPaceTracker tracker = new CallerPaceTracker(ON);

        tracker.record(s, words(8), 2000); // 4 words/s
        assertThat(s.callerPaceFactor()).as("one utterance decides nothing").isEqualTo(1.0);
        tracker.record(s, words(8), 2000);

        assertThat(s.callerPaceFactor()).isEqualTo(1.06);
        assertThat(s.isCallerHurried()).isTrue();
    }

    @Test
    void aSlowCallerSlowsItDownWithinTheBound() {
        DialogSession s = session();
        CallerPaceTracker tracker = new CallerPaceTracker(ON);

        tracker.record(s, words(3), 2000); // 1.5 words/s
        tracker.record(s, words(3), 2000);

        assertThat(s.callerPaceFactor()).isEqualTo(0.94);
        assertThat(s.isCallerHurried()).isFalse();
    }

    @Test
    void anOrdinaryCallerMovesItOnlyAsFarAsTheirPaceDiffers() {
        DialogSession s = session();
        CallerPaceTracker tracker = new CallerPaceTracker(ON);

        tracker.record(s, words(5), 2000); // 2.5 words/s
        tracker.record(s, words(5), 1950);

        assertThat(s.callerPaceFactor()).isBetween(1.0, 1.02);
    }

    @Test
    void offOrTooShortChangesNothing() {
        DialogSession s = session();
        new CallerPaceTracker(new CallerPaceProperties(false, 2.5, 6, 1.3)).record(s, words(8), 2000);
        new CallerPaceTracker(new CallerPaceProperties(false, 2.5, 6, 1.3)).record(s, words(8), 2000);
        CallerPaceTracker tracker = new CallerPaceTracker(ON);
        tracker.record(s, "ha", 400);
        tracker.record(s, "ha", 400);

        assertThat(s.callerPaceFactor()).isEqualTo(1.0);
        assertThat(s.isCallerHurried()).isFalse();
    }

    @Test
    void theFactorIsClampedBothWays() {
        assertThat(CallerPaceTracker.factorFor(2.0, 6)).isEqualTo(1.06);
        assertThat(CallerPaceTracker.factorFor(0.3, 6)).isEqualTo(0.94);
        assertThat(CallerPaceTracker.factorFor(1.03, 6)).isEqualTo(1.03);
        assertThat(CallerPaceTracker.factorFor(1.5, 0)).isEqualTo(1.0);
    }
}
