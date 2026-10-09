package uz.murodjon.robotcallv2.report.domain.entity;

import java.time.LocalDate;

/**
 * How one criterion was met in one day or week (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md §2.4).
 *
 * @param criterionId     null once the criterion has been deleted — its calls keep its name
 * @param criterionName   the name it was scored under most recently
 * @param periodStart     the company's day, or the Monday its week starts on
 * @param applied         answers YES or NO — NOT_APPLICABLE is left out
 * @param passRatePercent YES × 100 ÷ applied; null when nothing applied
 */
public record CriterionTrendPoint(
        Long criterionId,
        String criterionName,
        LocalDate periodStart,
        long applied,
        long passed,
        Double passRatePercent
) {
}
