package uz.murodjon.robotcallv2.agent.tts;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FirstChunkWatchdogTest {

    @Test
    void aProviderThatStaysSilentInterruptsTheCallerAndCloseClearsIt() throws Exception {
        FirstChunkWatchdog watchdog = FirstChunkWatchdog.start(50);

        assertThatSleepIsInterrupted(2_000);

        assertThat(watchdog.timedOut()).isTrue();
        watchdog.close();
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    void audioInTimeKeepsTheCallerUninterrupted() throws Exception {
        FirstChunkWatchdog watchdog = FirstChunkWatchdog.start(50);
        watchdog.firstAudioArrived();

        Thread.sleep(150);

        assertThat(watchdog.timedOut()).isFalse();
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
        watchdog.close();
    }

    @Test
    void closingBeforeTheTimeoutNeverFires() throws Exception {
        FirstChunkWatchdog watchdog = FirstChunkWatchdog.start(50);
        watchdog.close();

        Thread.sleep(150);

        assertThat(watchdog.timedOut()).isFalse();
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    void aZeroTimeoutGuardsNothing() throws Exception {
        FirstChunkWatchdog watchdog = FirstChunkWatchdog.start(0);

        Thread.sleep(50);

        assertThat(watchdog.timedOut()).isFalse();
        watchdog.close();
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    private static void assertThatSleepIsInterrupted(long millis) {
        try {
            Thread.sleep(millis);
            throw new AssertionError("the watchdog should have interrupted the sleeping thread");
        } catch (InterruptedException expected) {
            // the flag is cleared by the exception; close() has nothing left to clear here
        }
    }
}
