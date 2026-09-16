package uz.murodjon.robotcallv2.report.domain.entity;

/**
 * One queue's service over a period (docs/INBOUND-PBX-PLAN.md §11.2).
 *
 * @param offered             calls that joined the queue
 * @param shortAbandoned      of the abandoned, those who gave up within 5 seconds — left out
 *                            of the service level and abandon rate, as is standard
 * @param answeredWithinSla   answered within the queue's {@code serviceLevelSec}
 * @param serviceLevelPercent answered within SLA ÷ (offered − short abandoned); null with no calls
 * @param abandonRatePercent  abandoned (not short) ÷ (offered − short abandoned); null with no calls
 * @param averageWaitSeconds  ASA — average wait of the answered
 * @param averageTalkSeconds  from the operator picking up to the end of the call
 * @param overflowed          sent on by the queue's overflow (waited too long, full, nobody in)
 */
public record QueuePerformance(
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
