package uz.murodjon.robotcallv2.report.application.dto;

public record TimelinePoint(
        String t,
        String label,
        long calls,
        long minutes,
        long failed,
        long completed,
        double successRate
) {}
