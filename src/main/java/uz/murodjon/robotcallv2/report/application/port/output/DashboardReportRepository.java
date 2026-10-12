package uz.murodjon.robotcallv2.report.application.port.output;

import uz.murodjon.robotcallv2.report.domain.entity.*;

import java.time.Instant;
import java.util.List;

/**
 * Period analytics: the dashboard, the charts of the Reports page and its export. Every
 * figure that takes a {@link DashboardFilter} counts the same calls — one join chain and
 * one WHERE — and the same success ({@code Disposition#isConversion}).
 */
public interface DashboardReportRepository {

    DashboardTotals findDashboardTotals(long companyId, DashboardFilter filter);

    /** The totals of the calls one operator took in {@code [from, to)}. */
    DashboardTotals findOperatorTotals(long companyId, Instant from, Instant to, long operatorUserId);

    List<DashboardBucket> findDashboardBuckets(long companyId, DashboardFilter filter, String granularity);

    /** The buckets, further narrowed to escalated (or not escalated) calls when {@code escalated} is set. */
    List<DashboardBucket> findDynamicsBuckets(long companyId, DashboardFilter filter, Boolean escalated,
                                              String granularity);

    /** Calls per disposition, most frequent first. */
    List<DashboardOutcome> findDashboardOutcomes(long companyId, DashboardFilter filter);

    List<HourlyHeatmapCell> findHourlyHeatmap(long companyId, DashboardFilter filter);

    /** Per-campaign figures in {@code [from, to)}; every campaign with calls when {@code campaignIds} is empty. */
    List<CampaignComparison> findCampaignComparison(long companyId, Instant from, Instant to, List<Long> campaignIds);

    List<DurationHistogramBucket> findDurationHistogram(long companyId, DashboardFilter filter);

    List<FunnelStage> findFunnel(long companyId, DashboardFilter filter);

    /**
     * Per stage of the scenario agent {@code aiAgentId} speaks: calls that reached it, were lost in
     * it and converted after it; most reached first. By the agent the call ran rather than by a
     * scenario row, because an agent speaking its own embedded scenario (an installed pack) has none.
     */
    List<StageFunnelStep> findStageFunnel(long companyId, DashboardFilter filter, long aiAgentId);

    /** What AI-agent calls were about, most frequent first, at most {@code limit}; one agent's when {@code aiAgentId} is set. */
    List<AgentCallTopic> findAgentCallTopics(long companyId, DashboardFilter filter, Long aiAgentId, int limit);

    /** Per campaign with calls in {@code [from, to)}: how much of its contact list was wrong; worst first. */
    List<CampaignDataQuality> findCampaignDataQuality(long companyId, Instant from, Instant to);

    DashboardAggregates findDashboardAggregates(long companyId, DashboardFilter filter);

    List<DashboardTimelineBucket> findDashboardTimelineBuckets(long companyId, DashboardFilter filter,
                                                               String granularity);

    List<DashboardDirectionTotals> findDashboardDirectionTotals(long companyId, DashboardFilter filter);

    /** Whoever took the most calls — operator or AI agent — at most {@code limit}. */
    List<DashboardAgentTotals> findDashboardTopAgents(long companyId, DashboardFilter filter, int limit);

    /** The newest campaigns and how far through their lists they are, at most {@code limit}. */
    List<DashboardCampaignProgress> findDashboardActiveCampaigns(long companyId, int limit);

    /** Calls in progress right now, newest first, at most {@code limit}. */
    List<DashboardLiveCall> findDashboardLiveCalls(long companyId, int limit);

    List<DashboardRecentCall> findDashboardRecentCalls(long companyId, DashboardFilter filter, int limit);
}
