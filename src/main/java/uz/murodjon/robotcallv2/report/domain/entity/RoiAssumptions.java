package uz.murodjon.robotcallv2.report.domain.entity;

/**
 * What a human operator would have cost for the same calls — the yardstick the robot is
 * measured against. Shown next to the figures, because a saving is only as true as these.
 *
 * @param operatorHourlyCostUzs     an operator's hour, salary and taxes included
 * @param operatorSecondsPerAttempt what each call costs an operator besides the talking: dialling,
 *                                  ringing, writing up the result
 * @param operatorHoursPerMonth     one full-time operator's working hours in a month
 */
public record RoiAssumptions(
        long operatorHourlyCostUzs,
        int operatorSecondsPerAttempt,
        int operatorHoursPerMonth
) {
}
