package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.murodjon.robotcallv2.agent.dialog.NoInputAction.*;

/**
 * The watchdog is the only thing that notices a call where nothing is going to happen, so
 * its two mistakes are both bad: prompting over someone who is mid-sentence, and never
 * prompting at all while the line sits dead.
 */
class NoInputWatchdogTest {

    private static final Instant T0 = Instant.parse("2026-07-01T10:00:00Z");
    private static final Duration IDLE = Duration.ofSeconds(9);

    private NoInputWatchdog watchdog(int maxPrompts) {
        return new NoInputWatchdog(IDLE, maxPrompts, T0);
    }

    @Test
    void staysQuietBeforeTheThreshold() {
        NoInputWatchdog w = watchdog(2);
        assertThat(w.check(T0.plusSeconds(8), false, false)).isEqualTo(NONE);
    }

    @Test
    void promptsOnceTheLineHasBeenQuietLongEnough() {
        NoInputWatchdog w = watchdog(2);
        assertThat(w.check(T0.plusSeconds(9), false, false)).isEqualTo(PROMPT);
        assertThat(w.prompts()).isEqualTo(1);
    }

    @Test
    void aPromptRestartsTheClock() {
        NoInputWatchdog w = watchdog(2);
        assertThat(w.check(T0.plusSeconds(9), false, false)).isEqualTo(PROMPT);
        // Immediately after asking, the caller is owed time to answer.
        assertThat(w.check(T0.plusSeconds(10), false, false)).isEqualTo(NONE);
        assertThat(w.check(T0.plusSeconds(18), false, false)).isEqualTo(PROMPT);
    }

    @Test
    void endsTheCallOnceThePromptsAreSpent() {
        NoInputWatchdog w = watchdog(2);
        assertThat(w.check(T0.plusSeconds(9), false, false)).isEqualTo(PROMPT);
        assertThat(w.check(T0.plusSeconds(18), false, false)).isEqualTo(PROMPT);
        assertThat(w.check(T0.plusSeconds(27), false, false)).isEqualTo(END);
    }

    @Test
    void neverActsTwiceAfterEnding() {
        // The engine tears the call down on END; a second END would try to hang up a call
        // that is already gone.
        NoInputWatchdog w = watchdog(0);
        assertThat(w.check(T0.plusSeconds(9), false, false)).isEqualTo(END);
        assertThat(w.check(T0.plusSeconds(99), false, false)).isEqualTo(NONE);
    }

    @Test
    void zeroPromptsMeansEndWithoutAsking() {
        NoInputWatchdog w = watchdog(0);
        assertThat(w.check(T0.plusSeconds(9), false, false)).isEqualTo(END);
    }

    @Test
    void audioPlayingIsNotSilence() {
        NoInputWatchdog w = watchdog(2);
        // The bot is mid-utterance: the caller is being spoken to, not ignored.
        assertThat(w.check(T0.plusSeconds(30), true, false)).isEqualTo(NONE);
        // And the clock restarts from there, so a long reply does not immediately trip it.
        assertThat(w.check(T0.plusSeconds(35), false, false)).isEqualTo(NONE);
        assertThat(w.check(T0.plusSeconds(39), false, false)).isEqualTo(PROMPT);
    }

    @Test
    void aTurnInFlightIsNotSilence() {
        NoInputWatchdog w = watchdog(2);
        // The caller has spoken and an answer is being generated — the slowest legitimate
        // part of a turn, and exactly when a naive timer would talk over the reply.
        assertThat(w.check(T0.plusSeconds(30), false, true)).isEqualTo(NONE);
    }

    @Test
    void speechResetsTheClockEvenWithoutATranscript() {
        NoInputWatchdog w = watchdog(2);
        // What barge-in reports: somebody is talking, whether or not STT ever produces a
        // final for it.
        w.touch(T0.plusSeconds(8));
        assertThat(w.check(T0.plusSeconds(16), false, false)).isEqualTo(NONE);
        assertThat(w.check(T0.plusSeconds(17), false, false)).isEqualTo(PROMPT);
    }
}
