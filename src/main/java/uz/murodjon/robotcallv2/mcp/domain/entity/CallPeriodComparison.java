package uz.murodjon.robotcallv2.mcp.domain.entity;

import uz.murodjon.robotcallv2.report.domain.entity.DashboardOutcome;
import uz.murodjon.robotcallv2.report.domain.entity.ReportSummary;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Two periods' call figures side by side, with the differences already worked out — so the
 * copilot quotes a computed change instead of doing arithmetic in its head.
 *
 * @param answerRatePct            answered / total calls of the current period, in percent
 * @param previousAnswerRatePct    the same for the previous period
 * @param totalCallsChangePct      change of the call count, percent; null when the previous period had none
 * @param answerRateChangePoints   change of the answer rate, percentage points
 * @param avgDurationChangePct     change of the average answered duration, percent; null when either is unknown
 * @param dispositionShares        each outcome's share of all calls in the current period, percent
 * @param dispositionShareChangePoints each outcome's share change against the previous period, percentage points
 */
public record CallPeriodComparison(
        ReportSummary current,
        ReportSummary previous,
        double answerRatePct,
        double previousAnswerRatePct,
        Double totalCallsChangePct,
        double answerRateChangePoints,
        Double avgDurationChangePct,
        Map<String, Double> dispositionShares,
        Map<String, Double> dispositionShareChangePoints
) {

    public static CallPeriodComparison of(ReportSummary current, ReportSummary previous) {
        long total = current.totals().totalCalls();
        long previousTotal = previous.totals().totalCalls();
        double answerRate = percent(current.totals().answeredCalls(), total);
        double previousAnswerRate = percent(previous.totals().answeredCalls(), previousTotal);

        Map<String, Double> shares = shares(current);
        Map<String, Double> previousShares = shares(previous);
        Set<String> dispositions = new TreeSet<>(shares.keySet());
        dispositions.addAll(previousShares.keySet());
        Map<String, Double> shareChanges = new LinkedHashMap<>();
        for (String disposition : dispositions) {
            shareChanges.put(disposition, round(shares.getOrDefault(disposition, 0.0)
                    - previousShares.getOrDefault(disposition, 0.0)));
        }

        return new CallPeriodComparison(current, previous, answerRate, previousAnswerRate,
                changePct(total, previousTotal), round(answerRate - previousAnswerRate),
                changePct(current.totals().avgDurationSec(), previous.totals().avgDurationSec()),
                shares, shareChanges);
    }

    private static Map<String, Double> shares(ReportSummary summary) {
        long total = summary.totals().totalCalls();
        Map<String, Double> shares = new LinkedHashMap<>();
        for (DashboardOutcome outcome : summary.outcomes()) {
            shares.put(outcome.disposition(), percent(outcome.count(), total));
        }
        return shares;
    }

    private static Double changePct(Number value, Number previous) {
        if (value == null || previous == null || previous.doubleValue() == 0) {
            return null;
        }
        return round((value.doubleValue() - previous.doubleValue()) / previous.doubleValue() * 100.0);
    }

    private static double percent(long part, long whole) {
        return whole == 0 ? 0.0 : round(part * 100.0 / whole);
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
