package uz.murodjon.robotcallv2.report.application.dto;

public record DashboardCampaignItem(
        long id,
        String name,
        int progress,
        long done,
        long total
) {}
