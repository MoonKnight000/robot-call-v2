package uz.murodjon.robotcallv2.report.domain.entity;

import uz.murodjon.robotcallv2.report.domain.enums.AnalyticsDimension;
import uz.murodjon.robotcallv2.report.domain.enums.AnalyticsMetric;
import uz.murodjon.robotcallv2.report.domain.enums.WidgetType;

import java.time.Instant;

/**
 * A configurable widget inside an analytics board.
 */
public record AnalyticsBoardWidget(
        long id,
        long boardId,
        String title,
        WidgetType widgetType,
        AnalyticsMetric metric,
        AnalyticsDimension groupBy,
        String timeRange,
        int gridRow,
        int gridCol,
        int gridWidth,
        int gridHeight,
        Instant createdAt
) {
}
