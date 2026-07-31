package uz.murodjon.uysotvoice.report.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.audit.dto.AuditFilter;
import uz.murodjon.uysotvoice.audit.dto.AuditRow;
import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.report.dto.CallDetail;
import uz.murodjon.uysotvoice.report.dto.CallFilter;
import uz.murodjon.uysotvoice.report.dto.CallRow;
import uz.murodjon.uysotvoice.report.dto.CampaignStats;
import uz.murodjon.uysotvoice.report.dto.DashboardBucket;
import uz.murodjon.uysotvoice.report.dto.DashboardKpi;
import uz.murodjon.uysotvoice.report.dto.DashboardMetric;
import uz.murodjon.uysotvoice.report.dto.DashboardOutcome;
import uz.murodjon.uysotvoice.report.dto.DashboardRange;
import uz.murodjon.uysotvoice.report.dto.DashboardTotals;
import uz.murodjon.uysotvoice.report.dto.RecordingFile;
import uz.murodjon.uysotvoice.report.dto.RecordingLocation;
import uz.murodjon.uysotvoice.report.dto.RecordingRedirect;
import uz.murodjon.uysotvoice.report.repository.ReportRepository;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.exception.NotFoundException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Reporting API business logic (PROJECT.md §10 Bosqich 12): what the campaigns did,
 * call by call. Wraps the read-only {@link ReportRepository} queries and the audit
 * trail behind the shapes the controller returns.
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    /** Local recordings are stored with this prefix to distinguish them from object URLs. */
    private static final String FILE_PREFIX = "file:";

    private final ReportRepository reports;
    private final AuditService audit;

    public ReportService(ReportRepository reports, AuditService audit) {
        this.reports = reports;
        this.audit = audit;
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
        long total = reports.countRecentCalls();
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
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
     * Where the recording for {@code callId} lives (§11.3 — a recording is evidence in a
     * dispute, so it has to be retrievable without shell access to the box). Records the
     * download in the audit trail before handing back a local file.
     */
    public RecordingLocation resolveRecording(long callId) {
        String url = reports.recordingUrl(callId);
        if (url == null || url.isBlank()) {
            throw new NotFoundException("No recording for call " + callId);
        }
        if (!url.startsWith(FILE_PREFIX)) {
            // Object storage owns it; hand the caller a redirect rather than proxying
            // megabytes of audio through this service.
            return new RecordingRedirect(url);
        }
        Path path = Path.of(url.substring(FILE_PREFIX.length()));
        if (!Files.isReadable(path)) {
            // The retention sweep (§11.3) deletes recordings on schedule, so a missing file
            // is an ordinary outcome, not a fault.
            log.info("Recording for call {} is no longer on disk: {}", callId, path);
            throw new NotFoundException("Recording for call " + callId + " is gone");
        }
        audit.record("RECORDING_DOWNLOAD", "call", String.valueOf(callId), path.getFileName().toString());
        return new RecordingFile(path, "call-" + callId + ".wav");
    }

    /** Who changed what through the API (§11). */
    public PageableData<AuditRow> auditLog(AuditFilter filter) {
        List<AuditRow> rows = audit.recent(filter);
        long total = audit.count();
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

    /** "Natijalar taqsimoti" — disposition distribution over the window, not bucketed. */
    public List<DashboardOutcome> dashboardOutcomes(String from, String to, Long campaignId) {
        DashboardRange range = DashboardRange.of(from, to);
        return reports.dashboardOutcomes(range.from(), range.to(), campaignId);
    }

    private static double rate(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    private static double orZero(Double value) {
        return value != null ? value : 0.0;
    }
}
