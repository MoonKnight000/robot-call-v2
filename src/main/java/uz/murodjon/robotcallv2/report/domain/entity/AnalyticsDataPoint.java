package uz.murodjon.robotcallv2.report.domain.entity;

/**
 * A data point returned in an analytics breakdown query.
 */
public record AnalyticsDataPoint(
        String key,
        String label,
        double value,
        Double secondaryValue
) {
}
