package uz.murodjon.robotcallv2.report.domain.entity;

/** The four KPI cards on the dashboard (§10.2 UI-DESIGN.md). */
public record DashboardKpi(
        DashboardMetric totalCalls,
        DashboardMetric answeredRate,
        DashboardMetric avgDurationSec,
        DashboardMetric promises
) {
}

