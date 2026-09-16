package uz.murodjon.robotcallv2.report.application.dto;

/** One line of {@code GET /api/reports/queues}; the fields are those of {@code QueuePerformance}. */
public record QueueReportRow(
        long queueId,
        String queueName,
        long offered,
        long answered,
        long abandoned,
        long shortAbandoned,
        long answeredWithinSla,
        Double serviceLevelPercent,
        Double abandonRatePercent,
        Double averageWaitSeconds,
        Integer maxWaitSeconds,
        Double averageTalkSeconds,
        long overflowed,
        long callbacksRequested,
        Double averageSurveyScore
) {
}
