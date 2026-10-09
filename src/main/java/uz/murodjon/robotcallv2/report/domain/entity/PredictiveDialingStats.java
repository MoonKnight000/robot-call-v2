package uz.murodjon.robotcallv2.report.domain.entity;

/**
 * How a PREDICTIVE campaign's calls went over a period (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md §1.4).
 *
 * @param dialled                    calls placed
 * @param answered                   reached a person: joined to an operator, taken by the AI, or abandoned
 * @param toOperator                 joined to an operator
 * @param aiOverflow                 taken by the AI agent because no operator was free or answered
 * @param abandoned                  the client hung up while an operator was being found
 * @param voicemail                  an answering machine picked up
 * @param aiOverflowPercent          {@code aiOverflow} of {@code answered}; null with nothing answered
 * @param abandonRatePercent         {@code abandoned} of {@code answered}; null with nothing answered
 * @param averageOperatorWaitSeconds how long a joined client waited for their operator; null with none joined
 */
public record PredictiveDialingStats(
        long campaignId,
        long dialled,
        long answered,
        long toOperator,
        long aiOverflow,
        long abandoned,
        long voicemail,
        Double aiOverflowPercent,
        Double abandonRatePercent,
        Double averageOperatorWaitSeconds
) {
}
