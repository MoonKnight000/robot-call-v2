package uz.murodjon.robotcallv2.report.domain.entity;

public record DashboardDirectionRow(
        String direction,
        long totalCalls,
        long totalMinutes,
        int avgDurationSec,
        long completedCalls
) {}
