package uz.murodjon.robotcallv2.dialer.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.callrecord.domain.entity.PhoneCallHistory;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetScore;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromiseTally;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentPropensityScorerTest {

    @Test
    void noHistoryIsNeutralWithNoBestHour() {
        assertThat(PaymentPropensityScorer.score(null, null)).isEqualTo(new TargetScore(TargetScore.NEUTRAL, null));
    }

    @Test
    void oneMissedCallIsNotAVerdict() {
        // (0 + 1) / (1 + 2) = 1/3 → 50 − 6.7
        assertThat(PaymentPropensityScorer.score(new PhoneCallHistory(1, 0, 0, Map.of()), null).score()).isEqualTo(43);
    }

    @Test
    void aNumberThatPicksUpScoresHigherAndKeepsItsHour() {
        TargetScore score = PaymentPropensityScorer.score(new PhoneCallHistory(4, 4, 0, Map.of(10, 3, 18, 1)), null);

        assertThat(score.score()).isEqualTo(63); // 5/6 → 50 + 13.3
        assertThat(score.bestCallHour()).isEqualTo(10);
    }

    @Test
    void keptPromisesRaiseAndBrokenOnesLowerUpToTheirCap() {
        assertThat(PaymentPropensityScorer.score(null, new PaymentPromiseTally(2, 0)).score()).isEqualTo(80);
        assertThat(PaymentPropensityScorer.score(null, new PaymentPromiseTally(5, 0)).score()).isEqualTo(80);
        assertThat(PaymentPropensityScorer.score(null, new PaymentPromiseTally(0, 3)).score()).isEqualTo(20);
        assertThat(PaymentPropensityScorer.score(null, new PaymentPromiseTally(1, 1)).score()).isEqualTo(50);
    }

    @Test
    void refusalsCostUpToTheirCap() {
        // reach 7/12 → +3.3, refusals −15
        assertThat(PaymentPropensityScorer.score(new PhoneCallHistory(10, 6, 5, Map.of(12, 6)), null).score())
                .isEqualTo(38);
    }

    @Test
    void theScoreNeverLeavesItsRange() {
        PhoneCallHistory hopeless = new PhoneCallHistory(10, 0, 5, Map.of());
        assertThat(PaymentPropensityScorer.score(hopeless, new PaymentPromiseTally(0, 3)).score()).isZero();

        PhoneCallHistory reliable = new PhoneCallHistory(20, 20, 0, Map.of(9, 20));
        assertThat(PaymentPropensityScorer.score(reliable, new PaymentPromiseTally(4, 0)).score()).isEqualTo(98);
    }

    @Test
    void aTieGoesToTheEarliestHour() {
        TargetScore score = PaymentPropensityScorer.score(new PhoneCallHistory(4, 4, 0, Map.of(14, 2, 9, 2)), null);
        assertThat(score.bestCallHour()).isEqualTo(9);
    }
}
