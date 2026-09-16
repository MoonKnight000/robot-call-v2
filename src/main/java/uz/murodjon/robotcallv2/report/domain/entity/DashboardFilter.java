package uz.murodjon.robotcallv2.report.domain.entity;

import java.time.Instant;

/**
 * What every dashboard figure is measured over: the calls started in {@code [from, to)},
 * optionally narrowed to one campaign and to one scenario. One type for every figure, so
 * the KPI cards, the charts and the summary page cannot count different calls.
 *
 * @param campaignId only calls dialled for this campaign; null for all
 * @param scenarioId only calls whose agent speaks this scenario — the campaign's agent or
 *                   the inbound route's; null for all
 */
public record DashboardFilter(Instant from, Instant to, Long campaignId, Long scenarioId) {

    public static DashboardFilter of(DashboardRange range, Long campaignId, Long scenarioId) {
        return new DashboardFilter(range.from(), range.to(), campaignId, scenarioId);
    }
}
