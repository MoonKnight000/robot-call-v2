package uz.murodjon.uysotvoice.report.service;

import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.audit.dto.AuditFilter;
import uz.murodjon.uysotvoice.audit.dto.AuditLog;
import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.campaign.dto.CampaignTarget;
import uz.murodjon.uysotvoice.campaign.enums.TargetStatus;
import uz.murodjon.uysotvoice.campaign.repository.CampaignTargetRepository;
import uz.murodjon.uysotvoice.donotcall.enums.DoNotCallSource;
import uz.murodjon.uysotvoice.donotcall.repository.DoNotCallRepository;
import uz.murodjon.uysotvoice.report.dto.BulkCallActionRequest;
import uz.murodjon.uysotvoice.report.dto.BulkCallActionResult;
import uz.murodjon.uysotvoice.report.dto.CallDetail;
import uz.murodjon.uysotvoice.report.dto.CallFilter;
import uz.murodjon.uysotvoice.report.dto.CallRow;
import uz.murodjon.uysotvoice.report.dto.CampaignComparisonRow;
import uz.murodjon.uysotvoice.report.dto.CampaignStats;
import uz.murodjon.uysotvoice.report.dto.DashboardBucket;
import uz.murodjon.uysotvoice.report.dto.DashboardKpi;
import uz.murodjon.uysotvoice.report.dto.DashboardMetric;
import uz.murodjon.uysotvoice.report.dto.DashboardOutcome;
import uz.murodjon.uysotvoice.report.dto.DashboardRange;
import uz.murodjon.uysotvoice.report.dto.DashboardTotals;
import uz.murodjon.uysotvoice.report.dto.DurationHistogramBucket;
import uz.murodjon.uysotvoice.report.dto.FunnelStage;
import uz.murodjon.uysotvoice.report.dto.HourlyHeatmapCell;
import uz.murodjon.uysotvoice.report.dto.ReportSummary;
import uz.murodjon.uysotvoice.report.repository.ReportRepository;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.exception.NotFoundException;
import uz.murodjon.uysotvoice.shared.exception.ValidationException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Reporting API business logic (PROJECT.md §10 Bosqich 12): what the campaigns did,
 * call by call. Wraps the read-only {@link ReportRepository} queries and the audit
 * trail behind the shapes the controller returns.
 */
@Service
public class ReportService {

    private final ReportRepository reports;
    private final AuditService audit;
    private final CampaignTargetRepository targets;
    private final DoNotCallRepository doNotCall;

    public ReportService(ReportRepository reports, AuditService audit,
                         CampaignTargetRepository targets, DoNotCallRepository doNotCall) {
        this.reports = reports;
        this.audit = audit;
        this.targets = targets;
        this.doNotCall = doNotCall;
    }

    /** Aggregate outcome of one campaign: statuses, dispositions, promise rate. */
    public CampaignStats campaign(long id) {
        CampaignStats stats = reports.campaignStats(id);
        if (stats == null) {
            throw new NotFoundException("campaign", id);
        }
        return stats;
    }

    /** Calls of one campaign, newest first. */
    public PageableData<CallRow> campaignCalls(long id, CallFilter filter) {
        List<CallRow> rows = reports.callsOfCampaign(id, filter);
        long total = reports.countCallsOfCampaign(id);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    /** Calls across every campaign, newest first — the "what just happened" view. */
    public PageableData<CallRow> calls(CallFilter filter) {
        List<CallRow> rows = reports.recentCalls(filter);
        long total = reports.countRecentCalls(filter);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    /**
     * Calls matching {@code filter} for {@code POST /api/reports/calls/export} — unpaged
     * (up to {@link uz.murodjon.uysotvoice.shared.api.FilterInterface#MAX_SIZE}), and if
     * {@link CallFilter#ids()} is set, that selection wins over every other field (a
     * user-picked subset of rows, per §10.4 bulk export).
     */
    public List<CallRow> exportCalls(CallFilter filter) {
        return reports.exportCalls(filter);
    }

    /**
     * "Ommaviy amal paneli" (§10.4): re-queue each call's target for another attempt, or
     * opt each call's phone out. A bad id (wrong company, or the target/result it needs
     * no longer exists) is collected into {@code failed} rather than aborting the whole
     * request — the same "one bad row does not fail the batch" convention CSV import uses.
     */
    public BulkCallActionResult bulkAction(BulkCallActionRequest r) {
        List<Long> failed = new ArrayList<>();
        int processed = 0;
        for (long callId : r.ids()) {
            CallRow call = reports.findCall(callId);
            if (call == null) {
                failed.add(callId);
                continue;
            }
            if (applyBulkAction(r.action(), call)) {
                processed++;
            } else {
                failed.add(callId);
            }
        }
        audit.record("CALLS_BULK_" + r.action().toUpperCase(), "call", null,
                processed + " processed, " + failed.size() + " failed");
        return new BulkCallActionResult(processed, failed);
    }

    private boolean applyBulkAction(String action, CallRow call) {
        return switch (action) {
            case "retry" -> {
                CampaignTarget target = targets.find(call.targetId());
                if (target == null) {
                    yield false;
                }
                targets.updateStatus(target.id(), TargetStatus.PENDING, Instant.now());
                yield true;
            }
            case "dnc" -> {
                if (call.phone() == null || call.phone().isBlank()) {
                    yield false;
                }
                doNotCall.add(call.phone(), "opted out via bulk action", DoNotCallSource.MANUAL);
                yield true;
            }
            default -> throw new ValidationException("Unknown bulk action '" + action + "'");
        };
    }

    /** One call with its full transcript. */
    public CallDetail call(long callId) {
        CallDetail detail = reports.callDetail(callId);
        if (detail == null) {
            throw new NotFoundException("call", callId);
        }
        return detail;
    }

    /**
     * The {@code stored_file} id the recording for {@code callId} is catalogued under
     * (§11.3 — a recording is evidence in a dispute, so it has to be retrievable without
     * shell access to the box). The controller redirects here to {@code GET
     * /api/files/{id}}, which does the actual MinIO streaming.
     */
    public long resolveRecording(long callId) {
        Long fileId = reports.recordingFileId(callId);
        if (fileId == null) {
            throw new NotFoundException("No recording for call " + callId);
        }
        audit.record("RECORDING_DOWNLOAD", "call", String.valueOf(callId), String.valueOf(fileId));
        return fileId;
    }

    /** Who changed what through the API (§11). */
    public PageableData<AuditLog> auditLog(AuditFilter filter) {
        List<AuditLog> rows = audit.recent(filter);
        long total = audit.count(filter);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    /**
     * The 4 KPI cards on the dashboard (§10.2 UI-DESIGN.md): total calls, answered rate,
     * average duration, payment promises — each with its change against the previous
     * equal-length period and a per-bucket sparkline.
     *
     * @param from ISO-8601 instant, or omitted for {@code to} minus 7 days
     * @param to   ISO-8601 instant, or omitted for now
     */
    public DashboardKpi dashboardKpi(String from, String to, Long campaignId) {
        DashboardRange range = DashboardRange.of(from, to);
        DashboardTotals current = reports.dashboardTotals(range.from(), range.to(), campaignId);
        DashboardTotals previous = reports.dashboardTotals(
                range.previous().from(), range.previous().to(), campaignId);
        List<DashboardBucket> buckets = reports.dashboardBuckets(
                range.from(), range.to(), campaignId, range.granularity());

        DashboardMetric totalCalls = DashboardMetric.of(current.totalCalls(), previous.totalCalls(),
                buckets.stream().map(b -> (double) b.total()).toList());
        DashboardMetric answeredRate = DashboardMetric.of(
                rate(current.answeredCalls(), current.totalCalls()),
                rate(previous.answeredCalls(), previous.totalCalls()),
                buckets.stream().map(b -> rate(b.answered(), b.total())).toList());
        DashboardMetric avgDurationSec = DashboardMetric.of(
                orZero(current.avgDurationSec()), orZero(previous.avgDurationSec()),
                buckets.stream().map(b -> orZero(b.avgDurationSec())).toList());
        DashboardMetric promises = DashboardMetric.of(current.promises(), previous.promises(),
                buckets.stream().map(b -> (double) b.promises()).toList());

        return new DashboardKpi(totalCalls, answeredRate, avgDurationSec, promises);
    }

    /** "Qo'ng'iroqlar dinamikasi" stacked-area chart: javob berdi / bermadi / xato, bucketed. */
    public List<DashboardBucket> dashboardTimeseries(String from, String to, Long campaignId) {
        DashboardRange range = DashboardRange.of(from, to);
        return reports.dashboardBuckets(range.from(), range.to(), campaignId, range.granularity());
    }

    /**
     * "Qo'ng'iroqlar dinamikasi" for the Reports page (§10.10 Grafik 1) — the general
     * filter panel's version of {@link #dashboardTimeseries}, with the extra
     * scenario/operator narrowing that panel offers (see {@link
     * ReportRepository#dynamicsBuckets} for what "operator" maps to).
     */
    public List<DashboardBucket> dynamics(String from, String to, Long campaignId, Long scenarioId,
                                          Boolean escalated) {
        DashboardRange range = DashboardRange.of(from, to);
        return reports.dynamicsBuckets(range.from(), range.to(), campaignId, scenarioId, escalated, range.granularity());
    }

    /** "Natijalar taqsimoti" — disposition distribution over the window, not bucketed. */
    public List<DashboardOutcome> dashboardOutcomes(String from, String to, Long campaignId) {
        DashboardRange range = DashboardRange.of(from, to);
        return reports.dashboardOutcomes(range.from(), range.to(), campaignId);
    }

    /** "Kun × soat javob foizi" heatmap (§10.10 Grafik 3). */
    public List<HourlyHeatmapCell> hourlyHeatmap(String from, String to, Long campaignId) {
        DashboardRange range = DashboardRange.of(from, to);
        return reports.hourlyHeatmap(range.from(), range.to(), campaignId);
    }

    /** Side-by-side campaign KPI comparison (§10.10 Grafik 4). */
    public List<CampaignComparisonRow> campaignComparison(String from, String to, List<Long> campaignIds) {
        DashboardRange range = DashboardRange.of(from, to);
        return reports.campaignComparison(range.from(), range.to(), campaignIds);
    }

    /** Answered-call duration distribution (§10.10 Grafik 5). */
    public List<DurationHistogramBucket> durationHistogram(String from, String to, Long campaignId) {
        DashboardRange range = DashboardRange.of(from, to);
        return reports.durationHistogram(range.from(), range.to(), campaignId);
    }

    /** "Qo'ng'iroq → Javob → Shaxs tasdiqlandi → Suhbat → Natija" funnel (§10.10 Grafik 6). */
    public List<FunnelStage> funnel(String from, String to, Long campaignId) {
        DashboardRange range = DashboardRange.of(from, to);
        return reports.funnel(range.from(), range.to(), campaignId);
    }

    /**
     * The whole Reports page, bundled for {@code GET /api/reports/export} and the
     * scheduled email (§10.10). Campaign comparison is skipped once {@code campaignId}
     * already narrows the report to one campaign — comparing a campaign against itself
     * is not a report section, it is an empty one.
     */
    public ReportSummary summary(String from, String to, Long campaignId) {
        DashboardRange range = DashboardRange.of(from, to);
        DashboardTotals totals = reports.dashboardTotals(range.from(), range.to(), campaignId);
        List<DashboardOutcome> outcomes = reports.dashboardOutcomes(range.from(), range.to(), campaignId);
        List<FunnelStage> funnel = reports.funnel(range.from(), range.to(), campaignId);
        List<CampaignComparisonRow> campaigns = campaignId == null
                ? reports.campaignComparison(range.from(), range.to(), null)
                : List.of();
        return new ReportSummary(range.from(), range.to(), campaignId, totals, outcomes, funnel, campaigns);
    }

    private static double rate(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    private static double orZero(Double value) {
        return value != null ? value : 0.0;
    }
}
