package uz.murodjon.robotcallv2.campaign.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.campaign.domain.entity.OutcomeCondition;
import uz.murodjon.robotcallv2.campaign.domain.entity.OutcomeRoute;
import uz.murodjon.robotcallv2.campaign.domain.entity.SequenceOutcome;
import uz.murodjon.robotcallv2.campaign.domain.enums.OutcomeConditionOperator;
import uz.murodjon.robotcallv2.campaign.domain.enums.OutcomeEvaluation;
import uz.murodjon.robotcallv2.campaign.domain.enums.OutcomeMatch;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OutcomeMatcherTest {

    private static SequenceOutcome metrics(String id, OutcomeMatch match, OutcomeCondition... conditions) {
        return new SequenceOutcome(id, id, OutcomeEvaluation.METRICS, null, match, List.of(conditions), null,
                OutcomeRoute.END);
    }

    @Test
    void theFirstMatchingMetricsOutcomeInListOrderWins() {
        SequenceOutcome promised = metrics("promised", OutcomeMatch.ALL,
                new OutcomeCondition("disposition", OutcomeConditionOperator.EQUALS, "promise_to_pay"));
        SequenceOutcome anyAnswer = metrics("any", OutcomeMatch.ALL,
                new OutcomeCondition("disposition", OutcomeConditionOperator.IS_SET, null));

        assertThat(OutcomeMatcher.findMetricsMatch(List.of(promised, anyAnswer), Map.of("disposition", "PROMISE_TO_PAY")))
                .contains(promised);
    }

    @Test
    void numericOperatorsNeedNumbersOnBothSides() {
        SequenceOutcome bigDebt = metrics("big", OutcomeMatch.ALL,
                new OutcomeCondition("debt", OutcomeConditionOperator.GREATER_THAN, "1000000"));
        SequenceOutcome smallDebt = metrics("small", OutcomeMatch.ALL,
                new OutcomeCondition("debt", OutcomeConditionOperator.LESS_THAN, "1000000"));

        assertThat(OutcomeMatcher.findMetricsMatch(List.of(bigDebt), Map.of("debt", "2500000"))).contains(bigDebt);
        assertThat(OutcomeMatcher.findMetricsMatch(List.of(bigDebt, smallDebt), Map.of("debt", "ko'p"))).isEmpty();
    }

    @Test
    void anyNeedsOneConditionAndPromptOutcomesAreLeftToTheClassifier() {
        SequenceOutcome either = metrics("either", OutcomeMatch.ANY,
                new OutcomeCondition("city", OutcomeConditionOperator.EQUALS, "Toshkent"),
                new OutcomeCondition("city", OutcomeConditionOperator.EQUALS, "Samarqand"));
        SequenceOutcome prompt = SequenceOutcome.ofPrompt("p", "p", "anything", OutcomeRoute.END);

        assertThat(OutcomeMatcher.findMetricsMatch(List.of(prompt, either), Map.of("city", "samarqand"))).contains(either);
        assertThat(OutcomeMatcher.findMetricsMatch(List.of(prompt), Map.of("city", "samarqand"))).isEmpty();
    }
}
