package uz.murodjon.robotcallv2.report.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.audit.application.dto.AuditFilter;
import uz.murodjon.robotcallv2.audit.domain.entity.AuditLog;
import uz.murodjon.robotcallv2.report.application.dto.BulkCallActionRequest;
import uz.murodjon.robotcallv2.report.application.dto.BulkCallActionResult;
import uz.murodjon.robotcallv2.report.application.dto.CallFilter;
import uz.murodjon.robotcallv2.report.domain.entity.*;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RequestMapping("/api/reports")
public interface ReportController {

    @GetMapping("/campaigns/{id}")
    ResponseEntity<ResponseData<CampaignStats>> campaign(@PathVariable long id);

    @PostMapping("/campaigns/{id}/calls/list")
    ResponseEntity<ResponseData<PageableData<CallRow>>> campaignCalls(@PathVariable long id, @Valid @RequestBody CallFilter filter);

    @PostMapping("/calls/list")
    ResponseEntity<ResponseData<PageableData<CallRow>>> calls(@Valid @RequestBody CallFilter filter);

    @GetMapping("/calls/{callId}")
    ResponseEntity<ResponseData<CallDetail>> call(@PathVariable long callId);

    @PostMapping("/calls/export")
    ResponseEntity<byte[]> exportCalls(@Valid @RequestBody CallFilter filter);

    @PostMapping("/calls/bulk")
    ResponseEntity<ResponseData<BulkCallActionResult>> bulkAction(@Valid @RequestBody BulkCallActionRequest r);

    @GetMapping("/calls/{callId}/transcript.txt")
    ResponseEntity<Resource> transcript(@PathVariable long callId);

    /**
     * The call's audio, served here rather than redirected to {@code /api/files/{id}}:
     * the hop cost a player its credentials and left the recording unplayable. Comes
     * back {@code inline} with a length, and answers a {@code Range} with {@code 206}.
     */
    @GetMapping("/calls/{callId}/recording")
    ResponseEntity<Resource> recording(@PathVariable long callId,
                                       @RequestHeader(value = HttpHeaders.RANGE, required = false) String range);

    @PostMapping("/audit/list")
    ResponseEntity<ResponseData<PageableData<AuditLog>>> auditLog(@Valid @RequestBody AuditFilter filter);

    @GetMapping("/dashboard/kpi")
    ResponseEntity<ResponseData<DashboardKpi>> dashboardKpi(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long campaignId);

    @GetMapping("/dashboard/timeseries")
    ResponseEntity<ResponseData<List<DashboardBucket>>> dashboardTimeseries(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long campaignId);

    @GetMapping("/dashboard/outcomes")
    ResponseEntity<ResponseData<List<DashboardOutcome>>> dashboardOutcomes(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long campaignId);

    @GetMapping("/outcomes-distribution")
    ResponseEntity<ResponseData<List<DashboardOutcome>>> outcomesDistribution(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long campaignId);

    @GetMapping("/hourly-heatmap")
    ResponseEntity<ResponseData<List<HourlyHeatmapCell>>> hourlyHeatmap(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long campaignId);

    @GetMapping("/campaign-comparison")
    ResponseEntity<ResponseData<List<CampaignComparisonRow>>> campaignComparison(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) List<Long> campaignIds);

    @GetMapping("/duration-histogram")
    ResponseEntity<ResponseData<List<DurationHistogramBucket>>> durationHistogram(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long campaignId);

    @GetMapping("/dynamics")
    ResponseEntity<ResponseData<List<DashboardBucket>>> dynamics(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) Long scenarioId,
            @RequestParam(required = false) Boolean operator);

    @GetMapping("/funnel")
    ResponseEntity<ResponseData<List<FunnelStage>>> funnel(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long campaignId);

    @GetMapping("/export")
    ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long campaignId);
}
