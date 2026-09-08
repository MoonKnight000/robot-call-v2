package uz.murodjon.robotcallv2.report.application.dto;

public record DirectionStats(
        long count,
        int pct,
        long minutes,
        int avgDurationSec,
        double successRatePct
) {}
