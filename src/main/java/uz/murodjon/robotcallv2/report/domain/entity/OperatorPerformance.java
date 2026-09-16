package uz.murodjon.robotcallv2.report.domain.entity;

/**
 * One operator's work over a period (docs/INBOUND-PBX-PLAN.md §11.2).
 *
 * @param missed           offers they let ring out or rejected (RONA)
 * @param availableSeconds free or ringing
 * @param occupancyPercent (on call + wrap-up) ÷ (available + on call + wrap-up); null when never signed in
 */
public record OperatorPerformance(
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
