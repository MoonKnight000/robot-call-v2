package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** When a tool's request is sent again, and how long it waits first. */
class HttpToolExecutorRetryTest {

    @Test
    void retriesServerTroubleAndTryAgainLaterStatuses() {
        assertThat(HttpToolExecutor.isRetryable(500)).isTrue();
        assertThat(HttpToolExecutor.isRetryable(503)).isTrue();
        assertThat(HttpToolExecutor.isRetryable(408)).isTrue();
        assertThat(HttpToolExecutor.isRetryable(429)).isTrue();
    }

    @Test
    void doesNotRetryAnswersTheSameRequestWouldGetAgain() {
        assertThat(HttpToolExecutor.isRetryable(200)).isFalse();
        assertThat(HttpToolExecutor.isRetryable(400)).isFalse();
        assertThat(HttpToolExecutor.isRetryable(404)).isFalse();
    }

    @Test
    void backoffDoublesAndStaysBetweenHalfAndWholeOfItsCeiling() {
        for (int attempt = 0; attempt < 5; attempt++) {
            long ceiling = 300L << attempt;
            for (int sample = 0; sample < 50; sample++) {
                assertThat(HttpToolExecutor.backoffMillis(attempt)).isBetween(ceiling / 2, ceiling);
            }
        }
    }
}
