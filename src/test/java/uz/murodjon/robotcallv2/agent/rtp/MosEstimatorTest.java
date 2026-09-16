package uz.murodjon.robotcallv2.agent.rtp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MosEstimatorTest {

    @Test
    void cleanLineScoresNearTheNarrowbandCeiling() {
        assertThat(MosEstimator.estimate(0, 2)).isBetween(4.2, 4.4);
    }

    @Test
    void lossPullsTheScoreDown() {
        double clean = MosEstimator.estimate(0, 5);
        double lossy = MosEstimator.estimate(5, 5);
        double bad = MosEstimator.estimate(20, 5);
        assertThat(lossy).isLessThan(clean).isGreaterThan(3.5);
        assertThat(bad).isLessThan(3.0);
    }

    @Test
    void jitterPullsTheScoreDown() {
        assertThat(MosEstimator.estimate(1, 80)).isLessThan(MosEstimator.estimate(1, 5));
    }

    @Test
    void neverLeavesTheScale() {
        assertThat(MosEstimator.estimate(100, 1000)).isEqualTo(1.0);
        assertThat(MosEstimator.estimate(-3, -10)).isLessThanOrEqualTo(4.4);
    }

    @Test
    void statsWithoutPacketsHaveNoScore() {
        RtpStats stats = new RtpStats(8000);
        assertThat(stats.quality().mosEstimate()).isNull();
        assertThat(stats.quality().packetsReceived()).isZero();
    }
}
