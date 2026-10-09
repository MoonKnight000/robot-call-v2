package uz.murodjon.robotcallv2.report.domain.entity;

/**
 * How often one criterion was broken over a period (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md §2.4).
 *
 * @param criterionId     null once the criterion has been deleted — its calls keep its name
 * @param criterionName   the name it was scored under most recently
 * @param applied         answers YES or NO — NOT_APPLICABLE is left out
 * @param failed          answers NO
 * @param failRatePercent failed × 100 ÷ applied
 */
public record FailedCriterion(
        Long criterionId,
        String criterionName,
        long applied,
        long failed,
        double failRatePercent
) {
}
