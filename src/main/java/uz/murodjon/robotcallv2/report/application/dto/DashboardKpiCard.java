package uz.murodjon.robotcallv2.report.application.dto;

import java.util.List;

public record DashboardKpiCard(
        String id,
        String label,
        String value,
        Number rawValue,
        String unit,
        String delta,
        Double deltaPct,
        boolean up,
        Boolean isBad,
        List<Number> sparkline,
        String hint,
        String targetLink
) {}
