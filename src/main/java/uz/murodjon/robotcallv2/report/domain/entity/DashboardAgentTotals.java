package uz.murodjon.robotcallv2.report.domain.entity;

/**
 * Whoever took calls in the period — an operator or an AI agent — and how it went.
 *
 * @param type        {@code operator} or {@code ai}
 * @param successRate successful calls ({@code Disposition#isConversion}) per 100 calls
 */
public record DashboardAgentTotals(
        long id,
        String name,
        String type,
        long calls,
        long minutes,
        double successRate
) {
}
