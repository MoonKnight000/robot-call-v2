package uz.murodjon.robotcallv2.report.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.dto.AuditFilter;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.audit.domain.entity.AuditLog;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignTargetRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;
import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
import uz.murodjon.robotcallv2.donotcall.application.port.output.DoNotCallRepository;
import uz.murodjon.robotcallv2.donotcall.domain.enums.DoNotCallSource;
import uz.murodjon.robotcallv2.report.application.dto.BulkCallActionRequest;
import uz.murodjon.robotcallv2.report.application.dto.BulkCallActionResult;
import uz.murodjon.robotcallv2.report.application.dto.CallFilter;
import uz.murodjon.robotcallv2.report.application.port.input.ReportUseCase;
import uz.murodjon.robotcallv2.report.application.port.output.ReportRepository;
import uz.murodjon.robotcallv2.report.domain.entity.*;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReportService implements ReportUseCase {

    private final ReportRepository reports;
    private final AuditService audit;
    private final CampaignTargetRepository targets;
    private final DoNotCallRepository doNotCall;
    private final CurrentCompany currentCompany;

    public ReportService(ReportRepository reports, AuditService audit,
                         CampaignTargetRepository targets, DoNotCallRepository doNotCall,
                         CurrentCompany currentCompany) {
        this.reports = reports;
        this.audit = audit;
        this.targets = targets;
        this.doNotCall = doNotCall;
        this.currentCompany = currentCompany;
    }

    @Override
    public CampaignStats campaign(long id) {
        CampaignStats stats = reports.campaignStats(id);
        if (stats == null) {
            throw new NotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND, id);
        }
        return stats;
    }

    @Override
    public PageableData<CallRow> campaignCalls(long id, CallFilter filter) {
        List<CallRow> rows = reports.callsOfCampaign(id, filter);
        long total = reports.countCallsOfCampaign(id);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    @Override
    public PageableData<CallRow> calls(CallFilter filter) {
        List<CallRow> rows = reports.recentCalls(filter);
        long total = reports.countRecentCalls(filter);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    @Override
    public List<CallRow> exportCalls(CallFilter filter) {
        return reports.exportCalls(filter);
    }

    @Override
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
                doNotCall.add(currentCompany.id(), call.phone(), "opted out via bulk action", DoNotCallSource.MANUAL);
                yield true;
            }
            default -> throw new ValidationException(ErrorCode.REPORT_BULK_ACTION_UNKNOWN, action);
        };
    }

    @Override
    public CallDetail call(long callId) {
        CallDetail detail = reports.callDetail(callId);
        if (detail == null) {
            throw new NotFoundException(ErrorCode.CALL_NOT_FOUND, callId);
        }
        return detail;
    }

    @Override
    public long resolveRecording(long callId) {
        Long fileId = reports.recordingFileId(callId);
        if (fileId == null) {
            throw new NotFoundException(ErrorCode.CALL_RECORDING_NOT_FOUND, callId);
        }
        audit.record("RECORDING_DOWNLOAD", "call", String.valueOf(callId), String.valueOf(fileId));
        return fileId;
    }

    @Override
    public PageableData<AuditLog> auditLog(AuditFilter filter) {
        List<AuditLog> rows = audit.recent(filter);
        long total = audit.count(filter);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    @Override
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

    @Override
    public List<DashboardBucket> dashboardTimeseries(String from, String to, Long campaignId) {
        DashboardRange range = DashboardRange.of(from, to);
        return reports.dashboardBuckets(range.from(), range.to(), campaignId, range.granularity());
    }

    @Override
    public List<DashboardBucket> dynamics(String from, String to, Long campaignId, Long scenarioId,
                                          Boolean escalated) {
        DashboardRange range = DashboardRange.of(from, to);
        return reports.dynamicsBuckets(range.from(), range.to(), campaignId, scenarioId, escalated, range.granularity());
    }

    @Override
    public List<DashboardOutcome> dashboardOutcomes(String from, String to, Long campaignId) {
        DashboardRange range = DashboardRange.of(from, to);
        return reports.dashboardOutcomes(range.from(), range.to(), campaignId);
    }

    @Override
    public List<HourlyHeatmapCell> hourlyHeatmap(String from, String to, Long campaignId) {
        DashboardRange range = DashboardRange.of(from, to);
        return reports.hourlyHeatmap(range.from(), range.to(), campaignId);
    }

    @Override
    public List<CampaignComparisonRow> campaignComparison(String from, String to, List<Long> campaignIds) {
        DashboardRange range = DashboardRange.of(from, to);
        return reports.campaignComparison(range.from(), range.to(), campaignIds);
    }

    @Override
    public List<DurationHistogramBucket> durationHistogram(String from, String to, Long campaignId) {
        DashboardRange range = DashboardRange.of(from, to);
        return reports.durationHistogram(range.from(), range.to(), campaignId);
    }

    @Override
    public List<FunnelStage> funnel(String from, String to, Long campaignId) {
        DashboardRange range = DashboardRange.of(from, to);
        return reports.funnel(range.from(), range.to(), campaignId);
    }

    @Override
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
