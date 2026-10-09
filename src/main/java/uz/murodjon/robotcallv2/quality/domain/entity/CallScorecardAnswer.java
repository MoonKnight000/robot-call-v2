package uz.murodjon.robotcallv2.quality.domain.entity;

import uz.murodjon.robotcallv2.quality.domain.enums.CriterionAnswer;

/**
 * The answer on one criterion. The criterion's name and weight are copied in, so a criterion
 * edited or deleted later leaves the calls already scored as they were.
 *
 * @param id           null until stored
 * @param criterionId  null once the criterion has been deleted
 * @param answer       what counts now — the judge's, or a supervisor's correction of it
 * @param judgedAnswer what the judge said, kept when a supervisor corrects it
 * @param evidence     the transcript words the judge rested its answer on; null when there are none
 */
public record CallScorecardAnswer(
        Long id,
        Long criterionId,
        String criterionName,
        int weight,
        CriterionAnswer answer,
        CriterionAnswer judgedAnswer,
        String evidence
) {

    /** The judge's answer to {@code criterion}, its name and weight as they are now. */
    public static CallScorecardAnswer of(ScorecardCriterion criterion, CriterionAnswer answer, String evidence) {
        return new CallScorecardAnswer(null, criterion.id(), criterion.name(), criterion.weight(), answer, answer,
                evidence);
    }

    /** The same answer, as a supervisor settled it. */
    public CallScorecardAnswer withAnswer(CriterionAnswer corrected) {
        return new CallScorecardAnswer(id, criterionId, criterionName, weight, corrected, judgedAnswer, evidence);
    }
}
