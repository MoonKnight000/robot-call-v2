package uz.murodjon.robotcallv2.report.presentation.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.audit.domain.entity.AuditFilter;
import uz.murodjon.robotcallv2.audit.domain.entity.AuditLog;
import uz.murodjon.robotcallv2.report.application.dto.BulkCallActionRequest;
import uz.murodjon.robotcallv2.report.application.dto.BulkCallActionResult;
import uz.murodjon.robotcallv2.report.domain.entity.CallFilter;
import uz.murodjon.robotcallv2.report.application.port.input.ReportExportUseCase;
import uz.murodjon.robotcallv2.report.application.port.input.ReportUseCase;
import uz.murodjon.robotcallv2.report.domain.entity.*;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.storage.presentation.controller.FileResponseFactory;

import java.util.List;

@RestController
public class ReportControllerImpl implements ReportController {

    private final ReportUseCase reportUseCase;
    private final ReportExportUseCase reportExportUseCase;
    private final ReportDownloadResponseFactory reportDownloadResponseFactory;
    private final FileResponseFactory fileResponseFactory;

    public ReportControllerImpl(ReportUseCase reportUseCase,
                                ReportExportUseCase reportExportUseCase,
                                ReportDownloadResponseFactory reportDownloadResponseFactory,
                                FileResponseFactory fileResponseFactory) {
        this.reportUseCase = reportUseCase;
        this.reportExportUseCase = reportExportUseCase;
        this.reportDownloadResponseFactory = reportDownloadResponseFactory;
        this.fileResponseFactory = fileResponseFactory;
    }

    @Override
    public ResponseEntity<ResponseData<CampaignStats>> campaign(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(reportUseCase.campaign(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<CallRow>>> campaignCalls(long companyId, long id, CallFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(reportUseCase.campaignCalls(companyId, id, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<CallRow>>> calls(long companyId, CallFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(reportUseCase.calls(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<CallDetail>> call(long companyId, long callId) {
        return ResponseEntity.ok(ResponseData.ok(reportUseCase.call(companyId, callId)));
    }

    @Override
    public ResponseEntity<byte[]> exportCalls(long companyId, CallFilter filter) {
        return reportDownloadResponseFactory.toResponse(reportExportUseCase.exportCalls(companyId, filter));
    }

    @Override
    public ResponseEntity<ResponseData<BulkCallActionResult>> bulkAction(long companyId, BulkCallActionRequest request) {
        return ResponseEntity.ok(ResponseData.ok(reportUseCase.bulkAction(companyId, request)));
    }

    @Override
    public ResponseEntity<Resource> transcript(long companyId, long callId) {
        return reportDownloadResponseFactory.toResourceResponse(reportExportUseCase.exportTranscript(companyId, callId));
    }

    @Override
    public ResponseEntity<Resource> recording(long companyId, long callId, String range) {
        return fileResponseFactory.toResponse(reportUseCase.recording(companyId, callId), range);
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<AuditLog>>> auditLog(long companyId, AuditFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(reportUseCase.auditLog(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<DashboardKpi>> dashboardKpi(long companyId, String from, String to, Long campaignId) {
        return ResponseEntity.ok(ResponseData.ok(reportUseCase.dashboardKpi(companyId, from, to, campaignId)));
    }

    @Override
    public ResponseEntity<ResponseData<List<DashboardBucket>>> dashboardTimeseries(long companyId, String from, String to, Long campaignId) {
        return ResponseEntity.ok(ResponseData.ok(reportUseCase.dashboardTimeseries(companyId, from, to, campaignId)));
    }

    @Override
    public ResponseEntity<ResponseData<List<DashboardOutcome>>> dashboardOutcomes(long companyId, String from, String to, Long campaignId) {
        return ResponseEntity.ok(ResponseData.ok(reportUseCase.dashboardOutcomes(companyId, from, to, campaignId)));
    }

    @Override
    public ResponseEntity<ResponseData<List<DashboardOutcome>>> outcomesDistribution(long companyId, String from, String to, Long campaignId) {
        return dashboardOutcomes(companyId, from, to, campaignId);
    }

    @Override
    public ResponseEntity<ResponseData<List<HourlyHeatmapCell>>> hourlyHeatmap(long companyId, String from, String to, Long campaignId) {
        return ResponseEntity.ok(ResponseData.ok(reportUseCase.hourlyHeatmap(companyId, from, to, campaignId)));
    }

    @Override
    public ResponseEntity<ResponseData<List<CampaignComparisonRow>>> campaignComparison(long companyId, String from, String to, List<Long> campaignIds) {
        return ResponseEntity.ok(ResponseData.ok(reportUseCase.campaignComparison(companyId, from, to, campaignIds)));
    }

    @Override
    public ResponseEntity<ResponseData<List<DurationHistogramBucket>>> durationHistogram(long companyId, String from, String to, Long campaignId) {
        return ResponseEntity.ok(ResponseData.ok(reportUseCase.durationHistogram(companyId, from, to, campaignId)));
    }

    @Override
    public ResponseEntity<ResponseData<List<DashboardBucket>>> dynamics(long companyId, String from, String to, Long campaignId,
                                                                        Long scenarioId, Boolean operator) {
        return ResponseEntity.ok(ResponseData.ok(reportUseCase.dynamics(companyId, from, to, campaignId, scenarioId, operator)));
    }

    @Override
    public ResponseEntity<ResponseData<List<FunnelStage>>> funnel(long companyId, String from, String to, Long campaignId) {
        return ResponseEntity.ok(ResponseData.ok(reportUseCase.funnel(companyId, from, to, campaignId)));
    }

    @Override
    public ResponseEntity<byte[]> export(long companyId, String format, String from, String to, Long campaignId) {
        return reportDownloadResponseFactory.toResponse(
                reportExportUseCase.exportSummary(companyId, format, from, to, campaignId));
    }
}
