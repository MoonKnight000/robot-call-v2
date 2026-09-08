package uz.murodjon.robotcallv2.report.application.dto;

import java.util.List;

public record DashboardTimeline(
        String unit,
        long peakCalls,
        String peakLabel,
        long totalCalls,
        long totalMinutes,
        List<TimelinePoint> points
) {}
