package uz.murodjon.robotcallv2.report.domain.entity;

/**
 * The dashboard's figures for one call direction.
 *
 * @param direction      {@code inbound} or {@code outbound}
 * @param completedCalls calls that succeeded ({@code Disposition#isConversion})
 */
public record DashboardDirectionTotals(
        String direction,
        long totalCalls,
        long totalMinutes,
        int avgDurationSec,
        long completedCalls
) {
}
