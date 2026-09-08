package uz.murodjon.robotcallv2.report.application.port.input;

import uz.murodjon.robotcallv2.audit.domain.entity.AuditFilter;
import uz.murodjon.robotcallv2.audit.domain.entity.AuditLog;
import uz.murodjon.robotcallv2.report.application.dto.BulkCallActionRequest;
import uz.murodjon.robotcallv2.report.application.dto.BulkCallActionResult;
import uz.murodjon.robotcallv2.report.application.dto.DashboardSummaryResponse;
import uz.murodjon.robotcallv2.report.domain.entity.*;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.storage.application.dto.DownloadableFile;

import java.util.List;

public interface ReportUseCase {

    CampaignStats campaign(long companyId, long id);

    PageableData<CallRow> campaignCalls(long companyId, long id, CallFilter filter);

    PageableData<CallRow> calls(long companyId, CallFilter filter);

    List<CallRow> exportCalls(long companyId, CallFilter filter);

    BulkCallActionResult bulkAction(long companyId, BulkCallActionRequest request);

    CallDetail call(long companyId, long callId);

    DownloadableFile recording(long companyId, long callId);

    PageableData<AuditLog> auditLog(long companyId, AuditFilter filter);

    DashboardKpi dashboardKpi(long companyId, String from, String to, Long campaignId);

    List<DashboardBucket> dashboardTimeseries(long companyId, String from, String to, Long campaignId);

    List<DashboardBucket> dynamics(long companyId, String from, String to, Long campaignId, Long scenarioId,
                                   Boolean escalated);

    List<DashboardOutcome> dashboardOutcomes(long companyId, String from, String to, Long campaignId);

    List<HourlyHeatmapCell> hourlyHeatmap(long companyId, String from, String to, Long campaignId);

    List<CampaignComparisonRow> campaignComparison(long companyId, String from, String to, List<Long> campaignIds);

    List<DurationHistogramBucket> durationHistogram(long companyId, String from, String to, Long campaignId);

    List<FunnelStage> funnel(long companyId, String from, String to, Long campaignId);

    ReportSummary summary(long companyId, String from, String to, Long campaignId);

    DashboardSummaryResponse dashboardSummary(long companyId, String range, String from, String to,
                                              Long campaignId, Long scenarioId);
}
