package uz.murodjon.robotcallv2.report.application.dto;

public record DashboardRecentCallItem(
        String id,
        String name,
        String phone,
        String campaign,
        String agentName,
        String direction,
        String code,
        String dispositionLabel,
        String duration,
        int durationSec,
        String time,
        String date,
        boolean hasRecording
) {}
