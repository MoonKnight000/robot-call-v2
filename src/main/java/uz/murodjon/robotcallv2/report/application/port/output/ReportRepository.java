package uz.murodjon.robotcallv2.report.application.port.output;

import uz.murodjon.robotcallv2.contact.application.dto.ContactCallHistoryRow;
import uz.murodjon.robotcallv2.report.domain.entity.*;

import java.time.Instant;
import java.util.List;

/**
 * Output port SPI for read-only reporting queries.
 */
public interface ReportRepository {

    CampaignStats campaignStats(long companyId, long campaignId);

    InboundRouteStats inboundRouteStats(long companyId, long inboundRouteId);

    List<CallRow> callsOfCampaign(long companyId, long campaignId, CallFilter filter);

    long countCallsOfCampaign(long companyId, long campaignId);

    List<CallRow> recentCalls(long companyId, CallFilter filter);

    List<CallRow> exportCalls(long companyId, CallFilter filter);

    long countRecentCalls(long companyId, CallFilter filter);

    CallRow findCall(long companyId, long callId);

    CallDetail callDetail(long companyId, long callId);

    DashboardTotals dashboardTotals(long companyId, Instant from, Instant to, Long campaignId);

    DashboardTotals operatorTotals(long companyId, Instant from, Instant to, long operatorUserId);

    List<DashboardBucket> dashboardBuckets(long companyId, Instant from, Instant to, Long campaignId,
                                           String granularity);

    List<DashboardBucket> dynamicsBuckets(long companyId, Instant from, Instant to, Long campaignId,
                                          Long scenarioId, Boolean escalated, String granularity);

    List<DashboardOutcome> dashboardOutcomes(long companyId, Instant from, Instant to, Long campaignId);

    List<HourlyHeatmapCell> hourlyHeatmap(long companyId, Instant from, Instant to, Long campaignId);

    List<CampaignComparisonRow> campaignComparison(long companyId, Instant from, Instant to,
                                                   List<Long> campaignIds);

    List<DurationHistogramBucket> durationHistogram(long companyId, Instant from, Instant to, Long campaignId);

    List<FunnelStage> funnel(long companyId, Instant from, Instant to, Long campaignId);

    List<ContactCallHistoryRow> callsForPhone(long companyId, String phone, int limit);

    Long recordingFileId(long companyId, long callId);

    DashboardAggregates dashboardAggregates(long companyId, Instant from, Instant to, Long campaignId, Long scenarioId);

    List<DashboardTimelineBucket> dashboardTimelineBuckets(long companyId, Instant from, Instant to, Long campaignId,
                                                           Long scenarioId, String granularity);

    List<DashboardOutcome> dashboardStatusBreakdown(long companyId, Instant from, Instant to, Long campaignId,
                                                     Long scenarioId);

    List<DashboardDirectionRow> dashboardDirectionStats(long companyId, Instant from, Instant to, Long campaignId,
                                                        Long scenarioId);

    List<DashboardAgentRow> dashboardTopAgents(long companyId, Instant from, Instant to, Long campaignId,
                                              Long scenarioId, int limit);

    List<DashboardCampaignRow> dashboardActiveCampaigns(long companyId, int limit);

    List<DashboardLiveRow> dashboardLiveCalls(long companyId, int limit);

    List<DashboardRecentCallRow> dashboardRecentCalls(long companyId, Instant from, Instant to, Long campaignId,
                                                     Long scenarioId, int limit);
}
