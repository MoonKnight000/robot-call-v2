package uz.murodjon.robotcallv2.report.application.dto;

import uz.murodjon.robotcallv2.report.domain.entity.AnalyticsDataPoint;

import java.util.List;

public record AnalyticsQueryResult(
        String metric,
        String groupBy,
        List<AnalyticsDataPoint> data
) {
}
