package uz.murodjon.uysotvoice.report.dto;

/** The four KPI cards on the dashboard (§10.2 UI-DESIGN.md). */
public record DashboardKpi(
        DashboardMetric totalCalls,
        DashboardMetric answeredRate,
        DashboardMetric avgDurationSec,
        DashboardMetric promises
) {
}
