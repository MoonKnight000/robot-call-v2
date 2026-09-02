package uz.murodjon.robotcallv2.report.application.port.output;

import uz.murodjon.robotcallv2.contact.application.dto.ContactCallHistoryRow;
import uz.murodjon.robotcallv2.report.application.dto.CallFilter;
import uz.murodjon.robotcallv2.report.domain.entity.*;

import java.time.Instant;
import java.util.List;

/**
 * Output port SPI for read-only reporting queries.
 */
public interface ReportRepository {

    CampaignStats campaignStats(long campaignId);

    InboundRouteStats inboundRouteStats(long inboundRouteId);

    List<CallRow> callsOfCampaign(long campaignId, CallFilter filter);

    long countCallsOfCampaign(long campaignId);

    List<CallRow> recentCalls(CallFilter filter);

    List<CallRow> exportCalls(CallFilter filter);

    long countRecentCalls(CallFilter filter);

    CallRow findCall(long callId);

    CallDetail callDetail(long callId);

    DashboardTotals dashboardTotals(Instant from, Instant to, Long campaignId);

    DashboardTotals operatorTotals(Instant from, Instant to, long operatorUserId);

    List<DashboardBucket> dashboardBuckets(Instant from, Instant to, Long campaignId, String granularity);

    List<DashboardBucket> dynamicsBuckets(Instant from, Instant to, Long campaignId, Long scenarioId,
                                          Boolean escalated, String granularity);

    List<DashboardOutcome> dashboardOutcomes(Instant from, Instant to, Long campaignId);

    List<HourlyHeatmapCell> hourlyHeatmap(Instant from, Instant to, Long campaignId);

    List<CampaignComparisonRow> campaignComparison(Instant from, Instant to, List<Long> campaignIds);

    List<DurationHistogramBucket> durationHistogram(Instant from, Instant to, Long campaignId);

    List<FunnelStage> funnel(Instant from, Instant to, Long campaignId);

    List<ContactCallHistoryRow> callsForPhone(String phone, int limit);

    Long recordingFileId(long callId);
}
