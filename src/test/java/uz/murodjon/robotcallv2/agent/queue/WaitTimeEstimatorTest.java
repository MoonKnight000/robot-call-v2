package uz.murodjon.robotcallv2.agent.queue;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WaitTimeEstimatorTest {

    @Test
    void positionTimesHandleTimeOverOperators() {
        // 5 callers ahead × 120 s ÷ 2 operators = 300 s
        assertThat(WaitTimeEstimator.estimateMinutes(5, 120, 30, 2)).isEqualTo(5);
    }

    @Test
    void roundsUpAndNeverSaysZero() {
        assertThat(WaitTimeEstimator.estimateMinutes(1, 30, 30, 3)).isEqualTo(1);
        assertThat(WaitTimeEstimator.estimateMinutes(3, 50, 30, 2)).isEqualTo(2);
    }

    @Test
    void saysNothingItCannotBelieve() {
        assertThat(WaitTimeEstimator.estimateMinutes(5, 120, 10, 3)).isNull();
        assertThat(WaitTimeEstimator.estimateMinutes(5, 120, 30, 1)).isNull();
        assertThat(WaitTimeEstimator.estimateMinutes(5, 0, 30, 3)).isNull();
        assertThat(WaitTimeEstimator.estimateMinutes(0, 120, 30, 3)).isNull();
    }
}
