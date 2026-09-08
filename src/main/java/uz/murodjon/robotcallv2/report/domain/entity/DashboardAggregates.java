package uz.murodjon.robotcallv2.report.domain.entity;

public record DashboardAggregates(
        long totalCalls,
        long totalMinutes,
        double avgDurationSec,
        long completedCalls,
        long failedCalls,
        long missedCalls
) {
    public static DashboardAggregates empty() {
        return new DashboardAggregates(0L, 0L, 0.0, 0L, 0L, 0L);
    }
}
