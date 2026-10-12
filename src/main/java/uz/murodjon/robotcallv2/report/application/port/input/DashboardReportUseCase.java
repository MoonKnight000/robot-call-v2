package uz.murodjon.robotcallv2.report.application.port.input;

import uz.murodjon.robotcallv2.report.application.dto.CampaignComparisonRow;
import uz.murodjon.robotcallv2.report.application.dto.DashboardSummaryResponse;
import uz.murodjon.robotcallv2.report.domain.entity.*;

import java.time.Instant;
import java.util.List;

/**
 * Period analytics: the dashboard, the Reports page charts and the summary its export is
 * built from. {@code from}/{@code to} are ISO-8601 instants or dates, resolved by
 * {@link DashboardRange}.
 */
public interface DashboardReportUseCase {

    DashboardKpi findDashboardKpi(long companyId, String from, String to, Long campaignId);

    List<DashboardBucket> findDashboardTimeseries(long companyId, String from, String to, Long campaignId);

    List<DashboardBucket> findDynamics(long companyId, String from, String to, Long campaignId, Long scenarioId,
                                       Boolean escalated);

    List<DashboardOutcome> findDashboardOutcomes(long companyId, String from, String to, Long campaignId);

    List<HourlyHeatmapCell> findHourlyHeatmap(long companyId, String from, String to, Long campaignId);

    List<CampaignComparisonRow> findCampaignComparison(long companyId, String from, String to, List<Long> campaignIds);

    List<DurationHistogramBucket> findDurationHistogram(long companyId, String from, String to, Long campaignId);

    List<FunnelStage> findFunnel(long companyId, String from, String to, Long campaignId);

    List<StageFunnelStep> findStageFunnel(long companyId, String from, String to, long aiAgentId, Long campaignId);

    List<AgentCallTopic> findAgentCallTopics(long companyId, String from, String to, Long aiAgentId, Long campaignId,
                                             Integer limit);

    List<CampaignDataQuality> findCampaignDataQuality(long companyId, String from, String to);

    ReportSummary findSummary(long companyId, String from, String to, Long campaignId);

    DashboardSummaryResponse findDashboardSummary(long companyId, String range, String from, String to,
                                                  Long campaignId, Long scenarioId);

    DashboardTotals findOperatorTotals(long companyId, Instant from, Instant to, long operatorUserId);
}
