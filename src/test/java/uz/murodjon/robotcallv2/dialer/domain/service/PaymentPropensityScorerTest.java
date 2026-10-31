package uz.murodjon.robotcallv2.dialer.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.callrecord.domain.entity.PhoneCallHistory;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetScore;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentPropensityScorerTest {

    @Test
    void noHistoryIsNeutralWithNoBestHour() {
        assertThat(PaymentPropensityScorer.score(null)).isEqualTo(new TargetScore(TargetScore.NEUTRAL, null));
    }

    @Test
    void oneMissedCallIsNotAVerdict() {
        // (0 + 1) / (1 + 2) = 1/3 → 50 − 6.7
        assertThat(PaymentPropensityScorer.score(new PhoneCallHistory(1, 0, 0, Map.of())).score()).isEqualTo(43);
    }

    @Test
    void aNumberThatPicksUpScoresHigherAndKeepsItsHour() {
        TargetScore score = PaymentPropensityScorer.score(new PhoneCallHistory(4, 4, 0, Map.of(10, 3, 18, 1)));

        assertThat(score.score()).isEqualTo(63); // 5/6 → 50 + 13.3
        assertThat(score.bestCallHour()).isEqualTo(10);
    }

    @Test
    void refusalsCostUpToTheirCap() {
        // reach 7/12 → +3.3, refusals −15
        assertThat(PaymentPropensityScorer.score(new PhoneCallHistory(10, 6, 5, Map.of(12, 6))).score())
                .isEqualTo(38);
    }

    @Test
    void theScoreStaysInsideItsRange() {
        // reach 1/12 → −16.7, refusals capped at −15
        PhoneCallHistory hopeless = new PhoneCallHistory(10, 0, 5, Map.of());
        assertThat(PaymentPropensityScorer.score(hopeless).score()).isEqualTo(18);

        // reach 21/22 → +18.2
        PhoneCallHistory reliable = new PhoneCallHistory(20, 20, 0, Map.of(9, 20));
        assertThat(PaymentPropensityScorer.score(reliable).score()).isEqualTo(68);
    }

    @Test
    void aTieGoesToTheEarliestHour() {
        TargetScore score = PaymentPropensityScorer.score(new PhoneCallHistory(4, 4, 0, Map.of(14, 2, 9, 2)));
        assertThat(score.bestCallHour()).isEqualTo(9);
    }
}
