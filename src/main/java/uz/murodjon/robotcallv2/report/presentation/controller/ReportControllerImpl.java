package uz.murodjon.robotcallv2.report.presentation.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.audit.application.dto.AuditFilter;
import uz.murodjon.robotcallv2.audit.domain.entity.AuditLog;
import uz.murodjon.robotcallv2.report.application.dto.BulkCallActionRequest;
import uz.murodjon.robotcallv2.report.application.dto.BulkCallActionResult;
import uz.murodjon.robotcallv2.report.application.dto.CallFilter;
import uz.murodjon.robotcallv2.report.application.port.input.ReportUseCase;
import uz.murodjon.robotcallv2.report.application.service.CsvResponseFactory;
import uz.murodjon.robotcallv2.report.application.service.ReportExportFactory;
import uz.murodjon.robotcallv2.report.application.service.TranscriptResponseFactory;
import uz.murodjon.robotcallv2.report.domain.entity.*;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.net.URI;
import java.util.List;

@RestController
public class ReportControllerImpl implements ReportController {

    private final ReportUseCase reportService;
    private final ReportExportFactory exportFactory;
    private final CsvResponseFactory csvResponseFactory;
    private final TranscriptResponseFactory transcriptResponseFactory;

    public ReportControllerImpl(ReportUseCase reportService,
                                ReportExportFactory exportFactory,
                                CsvResponseFactory csvResponseFactory,
                                TranscriptResponseFactory transcriptResponseFactory) {
        this.reportService = reportService;
        this.exportFactory = exportFactory;
        this.csvResponseFactory = csvResponseFactory;
        this.transcriptResponseFactory = transcriptResponseFactory;
    }

    @Override
    public ResponseEntity<ResponseData<CampaignStats>> campaign(long id) {
        return ResponseEntity.ok(ResponseData.ok(reportService.campaign(id)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<CallRow>>> campaignCalls(long id, CallFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(reportService.campaignCalls(id, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<CallRow>>> calls(CallFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(reportService.calls(filter)));
    }

    @Override
    public ResponseEntity<ResponseData<CallDetail>> call(long callId) {
        return ResponseEntity.ok(ResponseData.ok(reportService.call(callId)));
    }

    @Override
    public ResponseEntity<byte[]> exportCalls(CallFilter filter) {
        List<CallRow> rows = reportService.exportCalls(filter);
        return csvResponseFactory.toCsv("calls.csv", rows);
    }

    @Override
    public ResponseEntity<ResponseData<BulkCallActionResult>> bulkAction(BulkCallActionRequest r) {
        return ResponseEntity.ok(ResponseData.ok(reportService.bulkAction(r)));
    }

    @Override
    public ResponseEntity<Resource> transcript(long callId) {
        CallDetail detail = reportService.call(callId);
        return transcriptResponseFactory.toResponse(callId, detail);
    }

    @Override
    public ResponseEntity<Resource> recording(long callId) {
        long fileId = reportService.resolveRecording(callId);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/api/files/" + fileId))
                .build();
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<AuditLog>>> auditLog(AuditFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(reportService.auditLog(filter)));
    }

    @Override
    public ResponseEntity<ResponseData<DashboardKpi>> dashboardKpi(String from, String to, Long campaignId) {
        return ResponseEntity.ok(ResponseData.ok(reportService.dashboardKpi(from, to, campaignId)));
    }

    @Override
    public ResponseEntity<ResponseData<List<DashboardBucket>>> dashboardTimeseries(String from, String to, Long campaignId) {
        return ResponseEntity.ok(ResponseData.ok(reportService.dashboardTimeseries(from, to, campaignId)));
    }

    @Override
    public ResponseEntity<ResponseData<List<DashboardOutcome>>> dashboardOutcomes(String from, String to, Long campaignId) {
        return ResponseEntity.ok(ResponseData.ok(reportService.dashboardOutcomes(from, to, campaignId)));
    }

    @Override
    public ResponseEntity<ResponseData<List<DashboardOutcome>>> outcomesDistribution(String from, String to, Long campaignId) {
        return dashboardOutcomes(from, to, campaignId);
    }

    @Override
    public ResponseEntity<ResponseData<List<HourlyHeatmapCell>>> hourlyHeatmap(String from, String to, Long campaignId) {
        return ResponseEntity.ok(ResponseData.ok(reportService.hourlyHeatmap(from, to, campaignId)));
    }

    @Override
    public ResponseEntity<ResponseData<List<CampaignComparisonRow>>> campaignComparison(String from, String to, List<Long> campaignIds) {
        return ResponseEntity.ok(ResponseData.ok(reportService.campaignComparison(from, to, campaignIds)));
    }

    @Override
    public ResponseEntity<ResponseData<List<DurationHistogramBucket>>> durationHistogram(String from, String to, Long campaignId) {
        return ResponseEntity.ok(ResponseData.ok(reportService.durationHistogram(from, to, campaignId)));
    }

    @Override
    public ResponseEntity<ResponseData<List<DashboardBucket>>> dynamics(String from, String to, Long campaignId,
                                                                        Long scenarioId, Boolean operator) {
        return ResponseEntity.ok(ResponseData.ok(reportService.dynamics(from, to, campaignId, scenarioId, operator)));
    }

    @Override
    public ResponseEntity<ResponseData<List<FunnelStage>>> funnel(String from, String to, Long campaignId) {
        return ResponseEntity.ok(ResponseData.ok(reportService.funnel(from, to, campaignId)));
    }

    @Override
    public ResponseEntity<byte[]> export(String format, String from, String to, Long campaignId) {
        ReportSummary summary = reportService.summary(from, to, campaignId);
        return exportFactory.toResponse(format, summary);
    }
}
