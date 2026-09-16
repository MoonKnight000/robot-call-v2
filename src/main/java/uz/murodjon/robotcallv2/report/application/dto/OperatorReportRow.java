package uz.murodjon.robotcallv2.report.application.dto;

/** One line of {@code GET /api/reports/operators}; the fields are those of {@code OperatorPerformance}. */
public record OperatorReportRow(
        long userId,
        String name,
        long answered,
        long missed,
        Double averageTalkSeconds,
        long totalTalkSeconds,
        Double averageSurveyScore,
        long availableSeconds,
        long onCallSeconds,
        long wrapUpSeconds,
        long breakSeconds,
        Double occupancyPercent
) {
}
