package uz.murodjon.robotcallv2.report.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.report.domain.enums.AnalyticsDimension;
import uz.murodjon.robotcallv2.report.domain.enums.AnalyticsMetric;
import uz.murodjon.robotcallv2.report.domain.enums.WidgetType;

public record AddBoardWidgetRequest(
        @NotBlank String title,
        @NotNull WidgetType widgetType,
        @NotNull AnalyticsMetric metric,
        AnalyticsDimension groupBy,
        String timeRange,
        int gridRow,
        int gridCol,
        int gridWidth,
        int gridHeight
) {
}
