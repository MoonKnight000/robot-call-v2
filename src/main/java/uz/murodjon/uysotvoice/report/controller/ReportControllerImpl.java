package uz.murodjon.uysotvoice.report.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.uysotvoice.audit.dto.AuditFilter;
import uz.murodjon.uysotvoice.audit.dto.AuditRow;
import uz.murodjon.uysotvoice.report.dto.BulkCallActionRequest;
import uz.murodjon.uysotvoice.report.dto.BulkCallActionResult;
import uz.murodjon.uysotvoice.report.dto.CallDetail;
import uz.murodjon.uysotvoice.report.dto.CallFilter;
import uz.murodjon.uysotvoice.report.dto.CallRow;
import uz.murodjon.uysotvoice.report.dto.CampaignStats;
import uz.murodjon.uysotvoice.report.dto.DashboardBucket;
import uz.murodjon.uysotvoice.report.dto.DashboardKpi;
import uz.murodjon.uysotvoice.report.dto.DashboardOutcome;
import uz.murodjon.uysotvoice.report.service.ReportService;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

import java.util.List;

@RestController
public class ReportControllerImpl implements ReportController {

    private final ReportService service;
    private final RecordingResponseFactory recordings;
    private final CsvResponseFactory csv;
    private final TranscriptResponseFactory transcripts;

    public ReportControllerImpl(ReportService service, RecordingResponseFactory recordings,
                                CsvResponseFactory csv, TranscriptResponseFactory transcripts) {
        this.service = service;
        this.recordings = recordings;
        this.csv = csv;
        this.transcripts = transcripts;
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
        return recordings.toResponse(service.resolveRecording(callId));
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
    public ResponseEntity<ResponseData<PageableData<AuditRow>>> auditLog(AuditFilter filter) {
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
}
