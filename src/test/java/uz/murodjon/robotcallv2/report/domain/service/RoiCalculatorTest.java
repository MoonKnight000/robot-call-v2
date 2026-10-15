package uz.murodjon.robotcallv2.report.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.report.domain.entity.RoiAssumptions;
import uz.murodjon.robotcallv2.report.domain.entity.RoiFigures;
import uz.murodjon.robotcallv2.report.domain.entity.RoiTotals;

import static org.assertj.core.api.Assertions.assertThat;

/** Round numbers, worked out by hand in the comments. */
class RoiCalculatorTest {

    /** 36 000 so'm an operator hour, 60 s per call besides talking, 180 h a month. */
    private static final RoiAssumptions OPERATOR = new RoiAssumptions(36_000, 60, 180);

    @Test
    void chargesAnOperatorForEveryAttemptNotOnlyTheAnsweredOnes() {
        // 1 000 calls × 60 s + 300 answered × 120 s talk = 60 000 + 36 000 = 96 000 s = 26.7 h
        RoiTotals totals = new RoiTotals(1_000, 300, 36_000, 400_000, 50, 10_000_000, 20, 4_000_000, 25, 5_000_000);

        RoiFigures figures = RoiCalculator.calculate(totals, OPERATOR, 30);

        assertThat(figures.operatorHours()).isEqualTo(26.7);
        // 96 000 / 3 600 × 36 000 = 960 000
        assertThat(figures.operatorCostUzs()).isEqualTo(960_000);
        assertThat(figures.savedUzs()).isEqualTo(560_000);
        // 26.7 h of a 180 h month
        assertThat(figures.operatorEquivalent()).isEqualTo(0.1);
        assertThat(figures.costPerAnsweredCallUzs()).isEqualTo(1_333);
        assertThat(figures.costPerPromiseUzs()).isEqualTo(8_000);
    }

    @Test
    void returnIsCountedFromConversionsOnlyNotAlsoFromKeptPromises() {
        // (5 000 000 − 400 000) / 400 000 = 1150 %
        RoiTotals totals = new RoiTotals(1_000, 300, 36_000, 400_000, 50, 10_000_000, 20, 4_000_000, 25, 5_000_000);

        assertThat(RoiCalculator.calculate(totals, OPERATOR, 30).roiPercent()).isEqualTo(1150.0);
    }

    @Test
    void theOperatorEquivalentScalesWithThePeriod() {
        // 180 h of operator work in a 7-day period: 180 / (180 × 7 / 30) = 4.3 operators
        RoiTotals totals = new RoiTotals(0, 0, 648_000, 0, 0, 0, 0, 0, 0, 0);

        assertThat(RoiCalculator.calculate(totals, OPERATOR, 7).operatorEquivalent()).isEqualTo(4.3);
    }

    @Test
    void nothingSpentLeavesTheRatiosUndefinedRatherThanZero() {
        RoiFigures figures = RoiCalculator.calculate(new RoiTotals(0, 0, 0, 0, 0, 0, 0, 0, 0, 0), OPERATOR, 7);

        assertThat(figures.roiPercent()).isNull();
        assertThat(figures.costPerAnsweredCallUzs()).isNull();
        assertThat(figures.costPerPromiseUzs()).isNull();
        assertThat(figures.savedUzs()).isZero();
    }
}
