package uz.murodjon.robotcallv2.report.domain.entity;

public record DashboardAgentRow(
        long id,
        String name,
        String type,
        long calls,
        long minutes,
        double successRate
) {}
