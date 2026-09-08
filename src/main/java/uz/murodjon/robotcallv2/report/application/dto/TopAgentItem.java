package uz.murodjon.robotcallv2.report.application.dto;

public record TopAgentItem(
        Object id,
        String name,
        String role,
        String type,
        long calls,
        long minutes,
        double successRate,
        int relativePct
) {}
