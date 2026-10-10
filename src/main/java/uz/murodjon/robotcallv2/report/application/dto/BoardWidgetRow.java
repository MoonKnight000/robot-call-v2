package uz.murodjon.robotcallv2.report.application.dto;

import java.time.Instant;

public record BoardWidgetRow(
        long id,
        String title,
        String widgetType,
        String metric,
        String groupBy,
        String timeRange,
        int gridRow,
        int gridCol,
        int gridWidth,
        int gridHeight,
        Instant createdAt
) {
}
