package uz.murodjon.robotcallv2.report.application.dto;

public record StatusBreakdownItem(
        String code,
        String label,
        long count,
        int pct,
        String color
) {}
