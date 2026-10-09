package uz.murodjon.robotcallv2.quality.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.quality.domain.entity.CallScorecardAnswer;
import uz.murodjon.robotcallv2.quality.domain.enums.CriterionAnswer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreCalculatorTest {

    @Test
    void scoreIsTheWeightEarnedOverTheWeightThatApplied() {
        List<CallScorecardAnswer> answers = List.of(
                answer(3, CriterionAnswer.YES),
                answer(1, CriterionAnswer.NO));

        assertThat(ScoreCalculator.calculate(answers)).isEqualTo(75);
    }

    @Test
    void criterionThatDidNotComeUpCountsNeitherWay() {
        List<CallScorecardAnswer> answers = List.of(
                answer(2, CriterionAnswer.YES),
                answer(5, CriterionAnswer.NOT_APPLICABLE));

        assertThat(ScoreCalculator.calculate(answers)).isEqualTo(100);
    }

    @Test
    void nothingAppliedMeansNoScore() {
        assertThat(ScoreCalculator.calculate(List.of(answer(2, CriterionAnswer.NOT_APPLICABLE)))).isNull();
        assertThat(ScoreCalculator.calculate(List.of())).isNull();
    }

    @Test
    void scoreIsRounded() {
        List<CallScorecardAnswer> answers = List.of(
                answer(1, CriterionAnswer.YES),
                answer(1, CriterionAnswer.YES),
                answer(1, CriterionAnswer.NO));

        assertThat(ScoreCalculator.calculate(answers)).isEqualTo(67);
    }

    private static CallScorecardAnswer answer(int weight, CriterionAnswer answer) {
        return new CallScorecardAnswer(null, 1L, "c", weight, answer, answer, null);
    }
}
