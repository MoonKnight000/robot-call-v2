package uz.murodjon.uysotvoice.report.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.uysotvoice.audit.dto.AuditFilter;
import uz.murodjon.uysotvoice.audit.dto.AuditLog;
import uz.murodjon.uysotvoice.report.dto.BulkCallActionRequest;
import uz.murodjon.uysotvoice.report.dto.BulkCallActionResult;
import uz.murodjon.uysotvoice.report.dto.CallDetail;
import uz.murodjon.uysotvoice.report.dto.CallFilter;
import uz.murodjon.uysotvoice.report.dto.CallRow;
import uz.murodjon.uysotvoice.report.dto.CampaignComparisonRow;
import uz.murodjon.uysotvoice.report.dto.CampaignStats;
import uz.murodjon.uysotvoice.report.dto.DashboardBucket;
import uz.murodjon.uysotvoice.report.dto.DashboardKpi;
import uz.murodjon.uysotvoice.report.dto.DashboardOutcome;
import uz.murodjon.uysotvoice.report.dto.DurationHistogramBucket;
import uz.murodjon.uysotvoice.report.dto.FunnelStage;
import uz.murodjon.uysotvoice.report.dto.HourlyHeatmapCell;
import uz.murodjon.uysotvoice.report.service.ReportService;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

import java.net.URI;
import java.util.List;

@RestController
public class ReportControllerImpl implements ReportController {

    private final ReportService service;
    private final CsvResponseFactory csv;
    private final TranscriptResponseFactory transcripts;
    private final ReportExportFactory export;

    public ReportControllerImpl(ReportService service, CsvResponseFactory csv,
                                TranscriptResponseFactory transcripts, ReportExportFactory export) {
        this.service = service;
        this.csv = csv;
        this.transcripts = transcripts;
        this.export = export;
    }

    @Override
    public ResponseEntity<ResponseData<CampaignStats>> campaign(long id) {
        return ResponseEntity.ok(ResponseData.ok(service.campaign(id)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<CallRow>>> campaignCalls(long id, CallFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(service.campaignCalls(id, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<CallRow>>> calls(CallFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(service.calls(filter)));
    }

    @Override
    public ResponseEntity<ResponseData<CallDetail>> call(long callId) {
        return ResponseEntity.ok(ResponseData.ok(service.call(callId)));
    }

    @Override
    public ResponseEntity<Resource> recording(long callId) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/api/files/" + service.resolveRecording(callId)))
                .build();
    }

    @Override
    public ResponseEntity<byte[]> exportCalls(CallFilter filter) {
        return csv.toCsv("calls.csv", service.exportCalls(filter));
    }

    @Override
    public ResponseEntity<ResponseData<BulkCallActionResult>> bulkAction(BulkCallActionRequest r) {
        return ResponseEntity.ok(ResponseData.ok(service.bulkAction(r)));
    }

    @Override
    public ResponseEntity<Resource> transcript(long callId) {
        return transcripts.toResponse(callId, service.call(callId));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<AuditLog>>> auditLog(AuditFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(service.auditLog(filter)));
    }

    @Override
    public ResponseEntity<ResponseData<DashboardKpi>> dashboardKpi(String from, String to, Long campaignId) {
        return ResponseEntity.ok(ResponseData.ok(service.dashboardKpi(from, to, campaignId)));
    }

    @Override
    public ResponseEntity<ResponseData<List<DashboardBucket>>> dashboardTimeseries(String from, String to, Long campaignId) {
        return ResponseEntity.ok(ResponseData.ok(service.dashboardTimeseries(from, to, campaignId)));
    }

    @Override
    public ResponseEntity<ResponseData<List<DashboardOutcome>>> dashboardOutcomes(String from, String to, Long campaignId) {
        return ResponseEntity.ok(ResponseData.ok(service.dashboardOutcomes(from, to, campaignId)));
    }

    @Override
    public ResponseEntity<ResponseData<List<DashboardOutcome>>> outcomesDistribution(String from, String to, Long campaignId) {
        return ResponseEntity.ok(ResponseData.ok(service.dashboardOutcomes(from, to, campaignId)));
    }

    @Override
    public ResponseEntity<ResponseData<List<HourlyHeatmapCell>>> hourlyHeatmap(String from, String to, Long campaignId) {
        return ResponseEntity.ok(ResponseData.ok(service.hourlyHeatmap(from, to, campaignId)));
    }

    @Override
    public ResponseEntity<ResponseData<List<CampaignComparisonRow>>> campaignComparison(String from, String to, List<Long> campaignIds) {
        return ResponseEntity.ok(ResponseData.ok(service.campaignComparison(from, to, campaignIds)));
    }

    @Override
    public ResponseEntity<ResponseData<List<DurationHistogramBucket>>> durationHistogram(String from, String to, Long campaignId) {
        return ResponseEntity.ok(ResponseData.ok(service.durationHistogram(from, to, campaignId)));
    }

    @Override
    public ResponseEntity<ResponseData<List<DashboardBucket>>> dynamics(
            String from, String to, Long campaignId, Long scenarioId, Boolean operator) {
        return ResponseEntity.ok(ResponseData.ok(service.dynamics(from, to, campaignId, scenarioId, operator)));
    }

    @Override
    public ResponseEntity<ResponseData<List<FunnelStage>>> funnel(String from, String to, Long campaignId) {
        return ResponseEntity.ok(ResponseData.ok(service.funnel(from, to, campaignId)));
    }

    @Override
    public ResponseEntity<byte[]> export(String format, String from, String to, Long campaignId) {
        return export.toResponse(format, service.summary(from, to, campaignId));
    }
}
