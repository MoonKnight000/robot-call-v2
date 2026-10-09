package uz.murodjon.robotcallv2.dialer.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.dialer.domain.entity.DialPacingStats;

import static org.assertj.core.api.Assertions.assertThat;

class DialPacingCalculatorTest {

    @Test
    void noFreeOperatorMeansNoCall() {
        assertThat(DialPacingCalculator.countCallsToPlace(0, 0, new DialPacingStats(100, 30, 0), 20)).isZero();
    }

    @Test
    void tooFewCallsDialOnePerOperator() {
        assertThat(DialPacingCalculator.countCallsToPlace(4, 0, new DialPacingStats(10, 2, 0), 20)).isEqualTo(4);
        assertThat(DialPacingCalculator.countCallsToPlace(4, 0, DialPacingStats.EMPTY, 20)).isEqualTo(4);
    }

    @Test
    void ratioFollowsTheAnswerRate() {
        // Half answered: two calls per operator.
        assertThat(DialPacingCalculator.countCallsToPlace(3, 0, new DialPacingStats(40, 20, 0), 20)).isEqualTo(6);
    }

    @Test
    void everyCallAnsweredDialsOnePerOperator() {
        assertThat(DialPacingCalculator.countCallsToPlace(5, 0, new DialPacingStats(40, 40, 0), 20)).isEqualTo(5);
    }

    @Test
    void ratioIsCapped() {
        // One in ten answered would be ten per operator; the cap holds it at three.
        assertThat(DialPacingCalculator.countCallsToPlace(2, 0, new DialPacingStats(100, 10, 0), 20)).isEqualTo(6);
    }

    @Test
    void overflowAtTheCeilingDropsToOnePerOperator() {
        assertThat(DialPacingCalculator.countCallsToPlace(3, 0, new DialPacingStats(40, 20, 4), 20)).isEqualTo(3);
    }

    @Test
    void overflowNearTheCeilingHalvesTheExtra() {
        // 3 of 20 answered = 15% ≥ 75% of 20%: ratio 2 eases to 1.5.
        assertThat(DialPacingCalculator.calculateRatio(new DialPacingStats(40, 20, 3), 20)).isEqualTo(1.5);
    }

    @Test
    void zeroCeilingNeverDialsAhead() {
        assertThat(DialPacingCalculator.countCallsToPlace(3, 0, new DialPacingStats(40, 20, 0), 0)).isEqualTo(3);
    }

    @Test
    void clientsAlreadyRingingAreSubtracted() {
        assertThat(DialPacingCalculator.countCallsToPlace(3, 4, new DialPacingStats(40, 20, 0), 20)).isEqualTo(2);
        assertThat(DialPacingCalculator.countCallsToPlace(3, 9, new DialPacingStats(40, 20, 0), 20)).isZero();
    }
}
