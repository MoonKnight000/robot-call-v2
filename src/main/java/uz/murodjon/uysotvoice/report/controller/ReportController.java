package uz.murodjon.uysotvoice.report.controller;

import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

import java.util.List;

/**
 * Reporting API (PROJECT.md §10 Bosqich 12): what the campaigns did, call by call.
 *
 * <p>Read-only, so a viewer-scoped API key is enough (see
 * {@code uz.murodjon.uysotvoice.security.SecurityConfig}) — reading results does not need the
 * key that can dial subscribers.
 */
@RequestMapping("/api/reports")
public interface ReportController {

    /** Aggregate outcome of one campaign: statuses, dispositions, promise rate. */
    @GetMapping("/campaigns/{id}")
    ResponseEntity<ResponseData<CampaignStats>> campaign(@PathVariable long id);

    /** Calls of one campaign, newest first. */
    @PostMapping("/campaigns/{id}/calls/list")
    ResponseEntity<ResponseData<PageableData<CallRow>>> campaignCalls(@PathVariable long id, @Valid @RequestBody CallFilter filter);

    /** Calls across every campaign, newest first — the "what just happened" view. */
    @PostMapping("/calls/list")
    ResponseEntity<ResponseData<PageableData<CallRow>>> calls(@Valid @RequestBody CallFilter filter);

    /** One call with its full transcript. */
    @GetMapping("/calls/{callId}")
    ResponseEntity<ResponseData<CallDetail>> call(@PathVariable long callId);

    /**
     * "⇩ Eksport" (§10.4). Filtered the same way as {@code POST /calls/list} (a plain
     * {@code GET} would need more than the 3 query parameters the project's filter-endpoint
     * convention allows), capped at {@link uz.murodjon.uysotvoice.shared.api.FilterInterface#MAX_SIZE}
     * rows. When {@link CallFilter#ids()} is set, exports exactly those calls instead — how
     * a user-selected subset gets exported.
     */
    @PostMapping("/calls/export")
    ResponseEntity<byte[]> exportCalls(@Valid @RequestBody CallFilter filter);

    /** Bulk "Qayta qo'ng'iroq" / "DNC ro'yxatiga" over selected rows (§10.4). */
    @PostMapping("/calls/bulk")
    ResponseEntity<ResponseData<BulkCallActionResult>> bulkAction(@Valid @RequestBody BulkCallActionRequest r);

    /** "TXT yuklab olish" (§10.5) — the transcript as plain text. */
    @GetMapping("/calls/{callId}/transcript.txt")
    ResponseEntity<Resource> transcript(@PathVariable long callId);

    /**
     * The call recording (§11.3 — a recording is evidence in a dispute, so it has to be
     * retrievable without shell access to the box). A 302 to {@code GET /api/files/{id}},
     * which streams it out of MinIO — never a raw MinIO URL, so a browser fetching this
     * same-origin keeps its {@code X-Api-Key}/Bearer header across the redirect. Returns
     * raw audio, not the {@code ResponseData} envelope other endpoints use.
     */
    @GetMapping("/calls/{callId}/recording")
    ResponseEntity<Resource> recording(@PathVariable long callId);

    /** Who changed what through the API (§11). */
    @PostMapping("/audit/list")
    ResponseEntity<ResponseData<PageableData<AuditLog>>> auditLog(@Valid @RequestBody AuditFilter filter);

    /**
     * The 4 KPI cards on the dashboard (§10.2 UI-DESIGN.md): total calls, answered rate,
     * average duration, payment promises — each with its change against the previous
     * equal-length period and a per-bucket sparkline.
     *
     * @param from ISO-8601 instant, or omitted for {@code to} minus 7 days
     * @param to   ISO-8601 instant, or omitted for now
     */
    @GetMapping("/dashboard/kpi")
    ResponseEntity<ResponseData<DashboardKpi>> dashboardKpi(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long campaignId);

    /** "Qo'ng'iroqlar dinamikasi" stacked-area chart: javob berdi / bermadi / xato, bucketed. */
    @GetMapping("/dashboard/timeseries")
    ResponseEntity<ResponseData<List<DashboardBucket>>> dashboardTimeseries(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long campaignId);

    /** "Natijalar taqsimoti" — disposition distribution over the window, not bucketed. */
    @GetMapping("/dashboard/outcomes")
    ResponseEntity<ResponseData<List<DashboardOutcome>>> dashboardOutcomes(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long campaignId);

    /**
     * "Natijalar taqsimoti" hisobotlar sahifasi uchun (§10.10 Grafik 2) — {@code
     * /dashboard/outcomes} bilan bir xil ma'lumot, alohida yo'l ostida.
     */
    @GetMapping("/outcomes-distribution")
    ResponseEntity<ResponseData<List<DashboardOutcome>>> outcomesDistribution(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long campaignId);

    /** "Kun × soat javob foizi" issiqlik xaritasi (§10.10 Grafik 3). */
    @GetMapping("/hourly-heatmap")
    ResponseEntity<ResponseData<List<HourlyHeatmapCell>>> hourlyHeatmap(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long campaignId);

    /**
     * Bir nechta kampaniyani yonma-yon solishtirish (§10.10 Grafik 4). {@code campaignIds}
     * berilmasa — davr ichida qo'ng'irog'i bo'lgan barcha kampaniyalar.
     */
    @GetMapping("/campaign-comparison")
    ResponseEntity<ResponseData<List<CampaignComparisonRow>>> campaignComparison(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) List<Long> campaignIds);

    /** Ulangan qo'ng'iroqlar davomiyligi taqsimoti (§10.10 Grafik 5). */
    @GetMapping("/duration-histogram")
    ResponseEntity<ResponseData<List<DurationHistogramBucket>>> durationHistogram(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long campaignId);

    /**
     * "Qo'ng'iroqlar dinamikasi" (§10.10 Grafik 1) — the reports page's own version of
     * {@code /dashboard/timeseries}, with the general filter panel's extra scenario and
     * operator (escalated-to-human) narrowing.
     */
    @GetMapping("/dynamics")
    ResponseEntity<ResponseData<List<DashboardBucket>>> dynamics(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) Long scenarioId,
            @RequestParam(required = false) Boolean operator);

    /** "Qo'ng'iroq → Javob → Shaxs tasdiqlandi → Suhbat → Natija" voronkasi (§10.10 Grafik 6). */
    @GetMapping("/funnel")
    ResponseEntity<ResponseData<List<FunnelStage>>> funnel(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long campaignId);

    /**
     * "⇩ Hisobotni yuklab olish" (§10.10) — the whole Reports page (KPI totals, outcome
     * distribution, funnel, campaign comparison) as one file. Not wrapped in {@code
     * ResponseData} (raw file body), like {@code /calls/export}.
     */
    @GetMapping("/export")
    ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long campaignId);
}
