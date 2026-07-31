package uz.murodjon.uysotvoice.report.dto;

import java.util.List;

/**
 * One KPI card's value (§10.2 UI-DESIGN.md): the number itself, its change against the
 * previous equal-length period, and the per-bucket trend behind the sparkline.
 *
 * @param changePct percent change vs. the previous period, or null when the previous
 *                  period was zero and the change is therefore undefined
 */
public record DashboardMetric(double value, Double changePct, List<Double> sparkline) {

    public static DashboardMetric of(double value, double previousValue, List<Double> sparkline) {
        Double changePct = previousValue == 0
                ? (value == 0 ? 0.0 : null)
                : (value - previousValue) / previousValue * 100.0;
        return new DashboardMetric(value, changePct, sparkline);
    }
}
