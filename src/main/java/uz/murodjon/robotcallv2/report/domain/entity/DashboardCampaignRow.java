package uz.murodjon.robotcallv2.report.domain.entity;

public record DashboardCampaignRow(
        long id,
        String name,
        long totalTargets,
        long doneTargets
) {}
