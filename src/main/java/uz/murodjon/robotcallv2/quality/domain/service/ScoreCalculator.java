package uz.murodjon.robotcallv2.quality.domain.service;

import uz.murodjon.robotcallv2.quality.domain.entity.CallScorecardAnswer;
import uz.murodjon.robotcallv2.quality.domain.enums.CriterionAnswer;

import java.util.List;

/**
 * A call's 0–100: the weight it earned over the weight that applied. A criterion that did
 * not come up is left out on both sides, so a short call is not marked down for what it
 * never had a chance to do.
 */
public final class ScoreCalculator {

    private ScoreCalculator() {
    }

    /** The score, or null when nothing applied. */
    public static Integer calculate(List<CallScorecardAnswer> answers) {
        int applied = 0;
        int earned = 0;
        for (CallScorecardAnswer answer : answers) {
            if (answer.answer() == CriterionAnswer.NOT_APPLICABLE) {
                continue;
            }
            applied += answer.weight();
            if (answer.answer() == CriterionAnswer.YES) {
                earned += answer.weight();
            }
        }
        return applied == 0 ? null : Math.round(earned * 100f / applied);
    }
}
