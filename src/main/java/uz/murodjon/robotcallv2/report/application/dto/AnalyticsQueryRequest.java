package uz.murodjon.robotcallv2.report.application.dto;

import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.report.domain.enums.AnalyticsDimension;
import uz.murodjon.robotcallv2.report.domain.enums.AnalyticsMetric;

import java.time.Instant;

public record AnalyticsQueryRequest(
        @NotNull AnalyticsMetric metric,
        AnalyticsDimension groupBy,
        Instant from,
        Instant to,
        Long campaignId,
        Long agentId
) {
}
