package uz.murodjon.robotcallv2.report.application.port.input;

import uz.murodjon.robotcallv2.audit.application.dto.AuditFilter;
import uz.murodjon.robotcallv2.audit.domain.entity.AuditLog;
import uz.murodjon.robotcallv2.report.application.dto.BulkCallActionRequest;
import uz.murodjon.robotcallv2.report.application.dto.BulkCallActionResult;
import uz.murodjon.robotcallv2.report.application.dto.CallFilter;
import uz.murodjon.robotcallv2.report.domain.entity.*;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.storage.application.dto.DownloadableFile;

import java.util.List;

public interface ReportUseCase {

    CampaignStats campaign(long id);

    PageableData<CallRow> campaignCalls(long id, CallFilter filter);

    PageableData<CallRow> calls(CallFilter filter);

    List<CallRow> exportCalls(CallFilter filter);

    BulkCallActionResult bulkAction(BulkCallActionRequest r);

    CallDetail call(long callId);

    DownloadableFile recording(long callId);

    PageableData<AuditLog> auditLog(AuditFilter filter);

    DashboardKpi dashboardKpi(String from, String to, Long campaignId);

    List<DashboardBucket> dashboardTimeseries(String from, String to, Long campaignId);

    List<DashboardBucket> dynamics(String from, String to, Long campaignId, Long scenarioId, Boolean escalated);

    List<DashboardOutcome> dashboardOutcomes(String from, String to, Long campaignId);

    List<HourlyHeatmapCell> hourlyHeatmap(String from, String to, Long campaignId);

    List<CampaignComparisonRow> campaignComparison(String from, String to, List<Long> campaignIds);

    List<DurationHistogramBucket> durationHistogram(String from, String to, Long campaignId);

    List<FunnelStage> funnel(String from, String to, Long campaignId);

    ReportSummary summary(String from, String to, Long campaignId);
}
