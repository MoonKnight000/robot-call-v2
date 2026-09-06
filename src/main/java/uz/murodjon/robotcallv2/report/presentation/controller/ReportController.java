package uz.murodjon.robotcallv2.report.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.audit.domain.entity.AuditFilter;
import uz.murodjon.robotcallv2.audit.domain.entity.AuditLog;
import uz.murodjon.robotcallv2.report.application.dto.BulkCallActionRequest;
import uz.murodjon.robotcallv2.report.application.dto.BulkCallActionResult;
import uz.murodjon.robotcallv2.report.domain.entity.CallFilter;
import uz.murodjon.robotcallv2.report.domain.entity.*;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RequestMapping("/api/reports")
public interface ReportController {

    @PreAuthorize("hasAuthority('REPORT_READ')")
    @GetMapping("/campaigns/{id}")
    ResponseEntity<ResponseData<CampaignStats>> campaign(@CurrentCompanyId long companyId, @PathVariable long id);

    @PreAuthorize("hasAuthority('REPORT_READ')")
    @PostMapping("/campaigns/{id}/calls/list")
    ResponseEntity<ResponseData<PageableData<CallRow>>> campaignCalls(@CurrentCompanyId long companyId, @PathVariable long id,
            @Valid @RequestBody CallFilter filter);

    @PreAuthorize("hasAuthority('REPORT_READ')")
    @PostMapping("/calls/list")
    ResponseEntity<ResponseData<PageableData<CallRow>>> calls(@CurrentCompanyId long companyId,
            @Valid @RequestBody CallFilter filter);

    @PreAuthorize("hasAuthority('REPORT_READ')")
    @GetMapping("/calls/{callId}")
    ResponseEntity<ResponseData<CallDetail>> call(@CurrentCompanyId long companyId, @PathVariable long callId);

    @PreAuthorize("hasAuthority('REPORT_READ')")
    @PostMapping("/calls/export")
    ResponseEntity<byte[]> exportCalls(@CurrentCompanyId long companyId, @Valid @RequestBody CallFilter filter);

    @PreAuthorize("hasAuthority('REPORT_EDIT')")
    @PostMapping("/calls/bulk")
    ResponseEntity<ResponseData<BulkCallActionResult>> bulkAction(@CurrentCompanyId long companyId,
            @Valid @RequestBody BulkCallActionRequest request);

    @PreAuthorize("hasAuthority('REPORT_READ')")
    @GetMapping("/calls/{callId}/transcript.txt")
    ResponseEntity<Resource> transcript(@CurrentCompanyId long companyId, @PathVariable long callId);

    /**
     * The call's audio, served here rather than redirected to {@code /api/files/{id}}:
     * the hop cost a player its credentials and left the recording unplayable. Comes
     * back {@code inline} with a length, and answers a {@code Range} with {@code 206}.
     */
    @PreAuthorize("hasAuthority('REPORT_READ')")
    @GetMapping("/calls/{callId}/recording")
    ResponseEntity<Resource> recording(@CurrentCompanyId long companyId, @PathVariable long callId,
                                       @RequestHeader(value = HttpHeaders.RANGE, required = false) String range);

    @PreAuthorize("hasAuthority('AUDIT_READ')")
    @PostMapping("/audit/list")
    ResponseEntity<ResponseData<PageableData<AuditLog>>> auditLog(@CurrentCompanyId long companyId,
            @Valid @RequestBody AuditFilter filter);

    @PreAuthorize("hasAuthority('DASHBOARD_READ')")
    @GetMapping("/dashboard/kpi")
    ResponseEntity<ResponseData<DashboardKpi>> dashboardKpi(
            @CurrentCompanyId long companyId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long campaignId);

    @PreAuthorize("hasAuthority('DASHBOARD_READ')")
    @GetMapping("/dashboard/timeseries")
    ResponseEntity<ResponseData<List<DashboardBucket>>> dashboardTimeseries(
            @CurrentCompanyId long companyId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long campaignId);

    @PreAuthorize("hasAuthority('DASHBOARD_READ')")
    @GetMapping("/dashboard/outcomes")
    ResponseEntity<ResponseData<List<DashboardOutcome>>> dashboardOutcomes(
            @CurrentCompanyId long companyId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long campaignId);

    @PreAuthorize("hasAuthority('REPORT_READ')")
    @GetMapping("/outcomes-distribution")
    ResponseEntity<ResponseData<List<DashboardOutcome>>> outcomesDistribution(
            @CurrentCompanyId long companyId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long campaignId);

    @PreAuthorize("hasAuthority('REPORT_READ')")
    @GetMapping("/hourly-heatmap")
    ResponseEntity<ResponseData<List<HourlyHeatmapCell>>> hourlyHeatmap(
            @CurrentCompanyId long companyId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long campaignId);

    @PreAuthorize("hasAuthority('REPORT_READ')")
    @GetMapping("/campaign-comparison")
    ResponseEntity<ResponseData<List<CampaignComparisonRow>>> campaignComparison(
            @CurrentCompanyId long companyId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) List<Long> campaignIds);

    @PreAuthorize("hasAuthority('REPORT_READ')")
    @GetMapping("/duration-histogram")
    ResponseEntity<ResponseData<List<DurationHistogramBucket>>> durationHistogram(
            @CurrentCompanyId long companyId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long campaignId);

    @PreAuthorize("hasAuthority('REPORT_READ')")
    @GetMapping("/dynamics")
    ResponseEntity<ResponseData<List<DashboardBucket>>> dynamics(
            @CurrentCompanyId long companyId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) Long scenarioId,
            @RequestParam(required = false) Boolean operator);

    @PreAuthorize("hasAuthority('REPORT_READ')")
    @GetMapping("/funnel")
    ResponseEntity<ResponseData<List<FunnelStage>>> funnel(
            @CurrentCompanyId long companyId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long campaignId);

    @PreAuthorize("hasAuthority('REPORT_READ')")
    @GetMapping("/export")
    ResponseEntity<byte[]> export(
            @CurrentCompanyId long companyId,
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long campaignId);
}
