package uz.murodjon.robotcallv2.report.domain.service;

import uz.murodjon.robotcallv2.report.domain.entity.RoiAssumptions;
import uz.murodjon.robotcallv2.report.domain.entity.RoiFigures;
import uz.murodjon.robotcallv2.report.domain.entity.RoiTotals;

/**
 * The ROI page's arithmetic.
 *
 * <p>An operator is charged for every attempt, not only the answered ones: dialling a number
 * that rings out still takes their time, which is most of what a robot saves on a debt list.
 * Return is counted from reported conversions only — a kept promise is already one of them
 * (a reported payment is what keeps it), so adding the two would count the same money twice.
 */
public final class RoiCalculator {

    private static final double DAYS_PER_MONTH = 30.0;

    private RoiCalculator() {
    }

    /**
     * @param periodDays the length of the period the totals cover, for the operator equivalent
     */
    public static RoiFigures calculate(RoiTotals totals, RoiAssumptions assumptions, double periodDays) {
        double operatorSeconds = totals.talkSeconds()
                + (double) totals.totalCalls() * assumptions.operatorSecondsPerAttempt();
        double operatorHours = operatorSeconds / 3600.0;
        long operatorCost = Math.round(operatorHours * assumptions.operatorHourlyCostUzs());
        double operatorHoursInPeriod = assumptions.operatorHoursPerMonth() * periodDays / DAYS_PER_MONTH;
        double operatorEquivalent = operatorHoursInPeriod > 0 ? round1(operatorHours / operatorHoursInPeriod) : 0.0;
        Double roiPercent = totals.spentUzs() > 0
                ? round1((totals.conversionValueUzs() - totals.spentUzs()) * 100.0 / totals.spentUzs())
                : null;
        return new RoiFigures(
                divide(totals.spentUzs(), totals.answeredCalls()),
                divide(totals.spentUzs(), totals.promises()),
                round1(operatorHours),
                operatorCost,
                operatorCost - totals.spentUzs(),
                operatorEquivalent,
                roiPercent);
    }

    private static Long divide(long amount, long count) {
        return count > 0 ? Math.round((double) amount / count) : null;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
