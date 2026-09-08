package uz.murodjon.robotcallv2.report.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.audit.domain.entity.AuditFilter;
import uz.murodjon.robotcallv2.audit.domain.entity.AuditLog;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignTargetRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;
import uz.murodjon.robotcallv2.donotcall.application.port.output.DoNotCallRepository;
import uz.murodjon.robotcallv2.donotcall.domain.enums.DoNotCallSource;
import uz.murodjon.robotcallv2.report.application.dto.*;
import uz.murodjon.robotcallv2.report.application.port.input.ReportUseCase;
import uz.murodjon.robotcallv2.report.application.port.output.ReportRepository;
import uz.murodjon.robotcallv2.report.domain.entity.*;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.storage.application.dto.DownloadableFile;
import uz.murodjon.robotcallv2.storage.application.service.FileStorageService;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ReportService implements ReportUseCase {

    private final ReportRepository reports;
    private final AuditService audit;
    private final CampaignTargetRepository targets;
    private final DoNotCallRepository doNotCall;
    private final FileStorageService fileStorageService;

    public ReportService(ReportRepository reports, AuditService audit,
                         CampaignTargetRepository targets, DoNotCallRepository doNotCall,
                         FileStorageService fileStorageService) {
        this.reports = reports;
        this.audit = audit;
        this.targets = targets;
        this.doNotCall = doNotCall;
        this.fileStorageService = fileStorageService;
    }

    @Override
    public CampaignStats campaign(long companyId, long id) {
        CampaignStats stats = reports.campaignStats(companyId, id);
        if (stats == null) {
            throw new NotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND, id);
        }
        return stats;
    }

    @Override
    public PageableData<CallRow> campaignCalls(long companyId, long id, CallFilter filter) {
        List<CallRow> rows = reports.callsOfCampaign(companyId, id, filter);
        long total = reports.countCallsOfCampaign(companyId, id);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    @Override
    public PageableData<CallRow> calls(long companyId, CallFilter filter) {
        List<CallRow> rows = reports.recentCalls(companyId, filter);
        long total = reports.countRecentCalls(companyId, filter);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    @Override
    public List<CallRow> exportCalls(long companyId, CallFilter filter) {
        return reports.exportCalls(companyId, filter);
    }

    @Override
    public BulkCallActionResult bulkAction(long companyId, BulkCallActionRequest r) {
        List<Long> failed = new ArrayList<>();
        int processed = 0;
        for (long callId : r.ids()) {
            CallRow call = reports.findCall(companyId, callId);
            if (call == null) {
                failed.add(callId);
                continue;
            }
            if (applyBulkAction(companyId, r.action(), call)) {
                processed++;
            } else {
                failed.add(callId);
            }
        }
        audit.record(companyId, "CALLS_BULK_" + r.action().toUpperCase(), "call", null,
                processed + " processed, " + failed.size() + " failed");
        return new BulkCallActionResult(processed, failed);
    }

    private boolean applyBulkAction(long companyId, String action, CallRow call) {
        return switch (action) {
            case "retry" -> {
                CampaignTarget target = targets.find(companyId, call.targetId());
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
                doNotCall.add(companyId, call.phone(), "opted out via bulk action", DoNotCallSource.MANUAL);
                yield true;
            }
            default -> throw new ValidationException(ErrorCode.REPORT_BULK_ACTION_UNKNOWN, action);
        };
    }

    @Override
    public CallDetail call(long companyId, long callId) {
        CallDetail detail = reports.callDetail(companyId, callId);
        if (detail == null) {
            throw new NotFoundException(ErrorCode.CALL_NOT_FOUND, callId);
        }
        return detail;
    }

    @Override
    public DownloadableFile recording(long companyId, long callId) {
        Long fileId = reports.recordingFileId(companyId, callId);
        if (fileId == null) {
            throw new NotFoundException(ErrorCode.CALL_RECORDING_NOT_FOUND, callId);
        }
        audit.record(companyId, "RECORDING_DOWNLOAD", "call", String.valueOf(callId), String.valueOf(fileId));
        return fileStorageService.download(companyId, fileId);
    }

    @Override
    public PageableData<AuditLog> auditLog(long companyId, AuditFilter filter) {
        List<AuditLog> rows = audit.recent(companyId, filter);
        long total = audit.count(companyId, filter);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    @Override
    public DashboardKpi dashboardKpi(long companyId, String from, String to, Long campaignId) {
        DashboardRange range = DashboardRange.of(from, to);
        DashboardTotals current = reports.dashboardTotals(companyId, range.from(), range.to(), campaignId);
        DashboardTotals previous = reports.dashboardTotals(companyId,
                range.previous().from(), range.previous().to(), campaignId);
        List<DashboardBucket> buckets = reports.dashboardBuckets(companyId,
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
    public List<DashboardBucket> dashboardTimeseries(long companyId, String from, String to, Long campaignId) {
        DashboardRange range = DashboardRange.of(from, to);
        return reports.dashboardBuckets(companyId, range.from(), range.to(), campaignId, range.granularity());
    }

    @Override
    public List<DashboardBucket> dynamics(long companyId, String from, String to, Long campaignId, Long scenarioId,
                                          Boolean escalated) {
        DashboardRange range = DashboardRange.of(from, to);
        return reports.dynamicsBuckets(companyId, range.from(), range.to(), campaignId, scenarioId, escalated, range.granularity());
    }

    @Override
    public List<DashboardOutcome> dashboardOutcomes(long companyId, String from, String to, Long campaignId) {
        DashboardRange range = DashboardRange.of(from, to);
        return reports.dashboardOutcomes(companyId, range.from(), range.to(), campaignId);
    }

    @Override
    public List<HourlyHeatmapCell> hourlyHeatmap(long companyId, String from, String to, Long campaignId) {
        DashboardRange range = DashboardRange.of(from, to);
        return reports.hourlyHeatmap(companyId, range.from(), range.to(), campaignId);
    }

    @Override
    public List<CampaignComparisonRow> campaignComparison(long companyId, String from, String to,
                                                          List<Long> campaignIds) {
        DashboardRange range = DashboardRange.of(from, to);
        return reports.campaignComparison(companyId, range.from(), range.to(), campaignIds);
    }

    @Override
    public List<DurationHistogramBucket> durationHistogram(long companyId, String from, String to, Long campaignId) {
        DashboardRange range = DashboardRange.of(from, to);
        return reports.durationHistogram(companyId, range.from(), range.to(), campaignId);
    }

    @Override
    public List<FunnelStage> funnel(long companyId, String from, String to, Long campaignId) {
        DashboardRange range = DashboardRange.of(from, to);
        return reports.funnel(companyId, range.from(), range.to(), campaignId);
    }

    @Override
    public ReportSummary summary(long companyId, String from, String to, Long campaignId) {
        DashboardRange range = DashboardRange.of(from, to);
        DashboardTotals totals = reports.dashboardTotals(companyId, range.from(), range.to(), campaignId);
        List<DashboardOutcome> outcomes = reports.dashboardOutcomes(companyId, range.from(), range.to(), campaignId);
        List<FunnelStage> funnel = reports.funnel(companyId, range.from(), range.to(), campaignId);
        List<CampaignComparisonRow> campaigns = campaignId == null
                ? reports.campaignComparison(companyId, range.from(), range.to(), null)
                : List.of();
        return new ReportSummary(range.from(), range.to(), campaignId, totals, outcomes, funnel, campaigns);
    }

    @Override
    public DashboardSummaryResponse dashboardSummary(long companyId, String range, String from, String to,
                                                      Long campaignId, Long scenarioId) {
        String rangePreset = (range != null && !range.isBlank()) ? range : "24h";
        DashboardRange currentRange = DashboardRange.ofPreset(rangePreset, from, to);
        DashboardRange previousRange = currentRange.previous();

        // 1. Aggregates for current and previous period
        DashboardAggregates currentAgg = reports.dashboardAggregates(companyId, currentRange.from(), currentRange.to(),
                campaignId, scenarioId);
        DashboardAggregates prevAgg = reports.dashboardAggregates(companyId, previousRange.from(), previousRange.to(),
                campaignId, scenarioId);

        // 2. Timeline points & sparklines
        String granularity = currentRange.granularity();
        List<DashboardTimelineBucket> buckets = reports.dashboardTimelineBuckets(companyId, currentRange.from(),
                currentRange.to(), campaignId, scenarioId, granularity);

        ZoneId zone = ZoneId.of("Asia/Tashkent");
        List<TimelinePoint> points = new ArrayList<>();
        List<Number> sparkTotalCalls = new ArrayList<>();
        List<Number> sparkMinutes = new ArrayList<>();
        List<Number> sparkAvgDuration = new ArrayList<>();
        List<Number> sparkSuccessRate = new ArrayList<>();
        List<Number> sparkFailed = new ArrayList<>();
        List<Number> sparkMissed = new ArrayList<>();

        long peakCalls = 0;
        String peakLabel = "";

        if ("hour".equals(granularity)) {
            Map<Instant, DashboardTimelineBucket> bucketMap = new HashMap<>();
            for (DashboardTimelineBucket b : buckets) {
                if (b.bucketStart() != null) {
                    bucketMap.put(b.bucketStart().truncatedTo(ChronoUnit.HOURS), b);
                }
            }

            Instant start = currentRange.from().truncatedTo(ChronoUnit.HOURS);
            Instant end = currentRange.to();
            Instant cursor = start;
            while (cursor.isBefore(end) || cursor.equals(start)) {
                DashboardTimelineBucket b = bucketMap.get(cursor);
                long calls = b != null ? b.calls() : 0L;
                long minutes = b != null ? b.minutes() : 0L;
                long failed = b != null ? b.failed() : 0L;
                long completed = b != null ? b.completed() : 0L;
                long missed = b != null ? b.missed() : 0L;
                double avgDur = b != null ? b.avgDurationSec() : 0.0;
                double successRate = calls > 0 ? round1(completed * 100.0 / calls) : 0.0;

                String t = DateTimeFormatter.ofPattern("HH:00").withZone(zone).format(cursor);
                String label = "Soat " + t;

                if (calls > peakCalls || peakLabel.isEmpty()) {
                    peakCalls = calls;
                    peakLabel = label;
                }

                points.add(new TimelinePoint(t, label, calls, minutes, failed, completed, successRate));
                sparkTotalCalls.add(calls);
                sparkMinutes.add(minutes);
                sparkAvgDuration.add(Math.round(avgDur));
                sparkSuccessRate.add(successRate);
                sparkFailed.add(failed);
                sparkMissed.add(missed);

                cursor = cursor.plus(1, ChronoUnit.HOURS);
                if (cursor.isAfter(end)) {
                    break;
                }
            }
        } else {
            Map<LocalDate, DashboardTimelineBucket> bucketMap = new HashMap<>();
            for (DashboardTimelineBucket b : buckets) {
                if (b.bucketStart() != null) {
                    bucketMap.put(b.bucketStart().atZone(zone).toLocalDate(), b);
                }
            }

            LocalDate start = currentRange.from().atZone(zone).toLocalDate();
            LocalDate end = currentRange.to().atZone(zone).toLocalDate();
            LocalDate cursor = start;
            while (!cursor.isAfter(end)) {
                DashboardTimelineBucket b = bucketMap.get(cursor);
                long calls = b != null ? b.calls() : 0L;
                long minutes = b != null ? b.minutes() : 0L;
                long failed = b != null ? b.failed() : 0L;
                long completed = b != null ? b.completed() : 0L;
                long missed = b != null ? b.missed() : 0L;
                double avgDur = b != null ? b.avgDurationSec() : 0.0;
                double successRate = calls > 0 ? round1(completed * 100.0 / calls) : 0.0;

                String t = cursor.format(DateTimeFormatter.ofPattern("dd.MM"));
                String dowName = uzbekDayOfWeek(cursor.getDayOfWeek());
                String label = dowName + ", " + t;

                if (calls > peakCalls || peakLabel.isEmpty()) {
                    peakCalls = calls;
                    peakLabel = label;
                }

                points.add(new TimelinePoint(t, label, calls, minutes, failed, completed, successRate));
                sparkTotalCalls.add(calls);
                sparkMinutes.add(minutes);
                sparkAvgDuration.add(Math.round(avgDur));
                sparkSuccessRate.add(successRate);
                sparkFailed.add(failed);
                sparkMissed.add(missed);

                cursor = cursor.plusDays(1);
            }
        }

        DashboardTimeline timeline = new DashboardTimeline(
                granularity,
                peakCalls,
                peakLabel,
                currentAgg.totalCalls(),
                currentAgg.totalMinutes(),
                points
        );

        // 3. Build 6 KPI Cards
        List<DashboardKpiCard> kpis = List.of(
                buildKpiCard(
                        "total_calls",
                        "Jami qo'ng'iroqlar",
                        formatNumber(currentAgg.totalCalls()),
                        currentAgg.totalCalls(),
                        "ta",
                        currentAgg.totalCalls(),
                        prevAgg.totalCalls(),
                        false,
                        sparkTotalCalls,
                        "Tanlangan davrdagi barcha terilgan va kelib tushgan qo'ng'iroqlar",
                        "/calls"
                ),
                buildKpiCard(
                        "minutes_used",
                        "Ishlatilgan daqiqalar",
                        formatNumber(currentAgg.totalMinutes()) + " daq",
                        currentAgg.totalMinutes(),
                        "daq",
                        currentAgg.totalMinutes(),
                        prevAgg.totalMinutes(),
                        false,
                        sparkMinutes,
                        "AI ovozli agent va operatorlar suhbat davomiyligi",
                        "/reports"
                ),
                buildAvgDurationCard(
                        currentAgg.avgDurationSec(),
                        prevAgg.avgDurationSec(),
                        sparkAvgDuration
                ),
                buildSuccessRateCard(
                        currentAgg,
                        prevAgg,
                        sparkSuccessRate
                ),
                buildKpiCard(
                        "failed_calls",
                        "Muvaffaqiyatsiz qo'ng'iroqlar",
                        formatNumber(currentAgg.failedCalls()),
                        currentAgg.failedCalls(),
                        "ta",
                        currentAgg.failedCalls(),
                        prevAgg.failedCalls(),
                        true,
                        sparkFailed,
                        "SIP ulanish xatosi yoki tarmoq xatosi tufayli uzilganlar (ko'rib chiqish tavsiya etiladi)",
                        "/calls?disposition=FAILED"
                ),
                buildKpiCard(
                        "missed_calls",
                        "Javobsiz qo'ng'iroqlar",
                        formatNumber(currentAgg.missedCalls()),
                        currentAgg.missedCalls(),
                        "ta",
                        currentAgg.missedCalls(),
                        prevAgg.missedCalls(),
                        true,
                        sparkMissed,
                        "Gudok ketgan ammo mijoz javob bermagan yoki band bo'lganlar",
                        "/calls?disposition=NO_ANSWER"
                )
        );

        // 4. Status Breakdown
        List<DashboardOutcome> rawBreakdown = reports.dashboardStatusBreakdown(companyId, currentRange.from(),
                currentRange.to(), campaignId, scenarioId);
        List<StatusBreakdownItem> statusBreakdown = mapStatusBreakdown(rawBreakdown, currentAgg.totalCalls());

        // 5. Direction Mix
        List<DashboardDirectionRow> dirRows = reports.dashboardDirectionStats(companyId, currentRange.from(),
                currentRange.to(), campaignId, scenarioId);
        DirectionMix directionMix = mapDirectionMix(dirRows, currentAgg.totalCalls());

        // 6. Top Agents
        List<DashboardAgentRow> agentRows = reports.dashboardTopAgents(companyId, currentRange.from(),
                currentRange.to(), campaignId, scenarioId, 5);
        List<TopAgentItem> topAgents = mapTopAgents(agentRows);

        // 7. Active Campaigns
        List<DashboardCampaignRow> campRows = reports.dashboardActiveCampaigns(companyId, 5);
        List<DashboardCampaignItem> campaigns = campRows.stream().map(c -> {
            int progress = c.totalTargets() > 0 ? (int) Math.round((double) c.doneTargets() * 100 / c.totalTargets()) : 0;
            return new DashboardCampaignItem(c.id(), c.name(), progress, c.doneTargets(), c.totalTargets());
        }).toList();

        // 8. Live Calls
        List<DashboardLiveRow> liveRows = reports.dashboardLiveCalls(companyId, 5);
        List<DashboardLiveItem> live = liveRows.stream().map(l -> {
            long elapsedSec = l.startedAt() != null ? Math.max(0, Duration.between(l.startedAt(), Instant.now()).toSeconds()) : 0L;
            String timer = formatMMSS((int) elapsedSec);
            String displayName = l.phone() + (l.clientName() != null && !l.clientName().isBlank() ? " (" + l.clientName() + ")" : "");
            String line = "inbound".equalsIgnoreCase(l.direction()) ? "Kiruvchi liniya" : "Chiquvchi liniya";
            return new DashboardLiveItem("live_" + l.callId(), displayName,
                    l.campaignName() != null ? l.campaignName() : "To'g'ridan-to'g'ri", timer,
                    l.startedAt().toString(), line);
        }).toList();

        // 9. Recent Calls
        List<DashboardRecentCallRow> recentRows = reports.dashboardRecentCalls(companyId, currentRange.from(),
                currentRange.to(), campaignId, scenarioId, 6);
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(zone);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zone);
        List<DashboardRecentCallItem> recentCalls = recentRows.stream().map(r -> {
            String name = (r.clientName() != null && !r.clientName().isBlank()) ? r.clientName() : r.phone();
            String campName = (r.campaignName() != null && !r.campaignName().isBlank()) ? r.campaignName() : "To'g'ridan-to'g'ri";
            String agentName = (r.operatorName() != null && !r.operatorName().isBlank()) ? r.operatorName() : "AI Agent";
            String timeStr = r.startedAt() != null ? timeFormatter.format(r.startedAt()) : "";
            String dateStr = r.startedAt() != null ? dateFormatter.format(r.startedAt()) : "";
            return new DashboardRecentCallItem(
                    String.valueOf(r.callId()),
                    name,
                    r.phone(),
                    campName,
                    agentName,
                    r.direction(),
                    r.disposition(),
                    mapDispositionLabel(r.disposition()),
                    formatMMSS(r.durationSec()),
                    r.durationSec(),
                    timeStr,
                    dateStr,
                    r.hasRecording()
            );
        }).toList();

        String updatedAt = LocalTime.now(zone).format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        return new DashboardSummaryResponse(
                rangePreset,
                currentRange.from().toString(),
                currentRange.to().toString(),
                updatedAt,
                kpis,
                timeline,
                statusBreakdown,
                directionMix,
                topAgents,
                campaigns,
                live,
                recentCalls
        );
    }

    private static double computeDeltaPct(double current, double prev) {
        if (prev == 0.0) {
            return current == 0.0 ? 0.0 : 100.0;
        }
        return round1(((current - prev) / prev) * 100.0);
    }

    private static String formatDelta(double deltaPct) {
        if (deltaPct > 0) {
            return "+" + deltaPct + "%";
        }
        return deltaPct + "%";
    }

    private static DashboardKpiCard buildKpiCard(
            String id, String label, String value, Number rawValue, String unit,
            double current, double prev, boolean isBad, List<Number> sparkline,
            String hint, String targetLink) {
        double deltaPct = computeDeltaPct(current, prev);
        boolean up = deltaPct >= 0;
        return new DashboardKpiCard(
                id, label, value, rawValue, unit,
                formatDelta(deltaPct), deltaPct, up,
                isBad ? Boolean.TRUE : null,
                sparkline, hint, targetLink
        );
    }

    private static DashboardKpiCard buildAvgDurationCard(double currentSec, double prevSec, List<Number> sparkline) {
        long raw = Math.round(currentSec);
        long mins = raw / 60;
        long secs = raw % 60;
        String val = mins > 0 ? mins + " daq " + secs + " s" : secs + " s";
        double deltaPct = computeDeltaPct(currentSec, prevSec);
        boolean up = deltaPct >= 0;
        return new DashboardKpiCard(
                "avg_duration",
                "O'rtacha davomiylik",
                val,
                raw,
                "soniya",
                formatDelta(deltaPct),
                deltaPct,
                up,
                null,
                sparkline,
                "Har bir muvaffaqiyatli suhbatning o'rtacha uzunligi",
                "/reports"
        );
    }

    private static DashboardKpiCard buildSuccessRateCard(DashboardAggregates current, DashboardAggregates prev,
                                                        List<Number> sparkline) {
        double currentRate = current.totalCalls() > 0 ? round1(current.completedCalls() * 100.0 / current.totalCalls()) : 0.0;
        double prevRate = prev.totalCalls() > 0 ? round1(prev.completedCalls() * 100.0 / prev.totalCalls()) : 0.0;
        double deltaPct = round1(currentRate - prevRate);
        boolean up = deltaPct >= 0;
        return new DashboardKpiCard(
                "success_rate",
                "Muvaffaqiyat ko'rsatkichi",
                currentRate + "%",
                currentRate,
                "%",
                formatDelta(deltaPct),
                deltaPct,
                up,
                null,
                sparkline,
                "Ijobiy natija yoki maqsadga erishilgan qo'ng'iroqlar ulushi",
                "/reports"
        );
    }

    private static String formatNumber(long n) {
        return String.format(Locale.US, "%,d", n).replace(',', ' ');
    }

    private static String formatMMSS(int totalSec) {
        int m = totalSec / 60;
        int s = totalSec % 60;
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static String uzbekDayOfWeek(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY -> "Dush";
            case TUESDAY -> "Sesh";
            case WEDNESDAY -> "Chor";
            case THURSDAY -> "Pay";
            case FRIDAY -> "Juma";
            case SATURDAY -> "Shan";
            case SUNDAY -> "Yak";
        };
    }

    private static List<StatusBreakdownItem> mapStatusBreakdown(List<DashboardOutcome> outcomes, long totalCalls) {
        if (outcomes.isEmpty()) {
            return List.of(
                    new StatusBreakdownItem("COMPLETED", "Suhbat yakunlandi (Muvaffaqiyatli)", 0, 0, "var(--success, #10b981)"),
                    new StatusBreakdownItem("NO_ANSWER", "Javob berilmadi", 0, 0, "var(--neutral-400, #94a3b8)"),
                    new StatusBreakdownItem("FAILED", "Xatolik / Ulanmadi", 0, 0, "var(--danger, #ef4444)")
            );
        }
        return outcomes.stream().map(o -> {
            int pct = totalCalls > 0 ? (int) Math.round((double) o.count() * 100 / totalCalls) : 0;
            String code = o.disposition();
            String label = mapDispositionLabel(code);
            String color = mapDispositionColor(code);
            return new StatusBreakdownItem(code, label, o.count(), pct, color);
        }).toList();
    }

    private static String mapDispositionLabel(String code) {
        if (code == null) return "Noma'lum";
        return switch (code) {
            case "COMPLETED" -> "Suhbat yakunlandi (Muvaffaqiyatli)";
            case "PROMISE_TO_PAY" -> "To'lov va'da qilindi";
            case "NO_ANSWER" -> "Javob berilmadi";
            case "VOICEMAIL" -> "Avtojavoblagich / AMD";
            case "BUSY_AMD" -> "Band / Avtojavoblagich";
            case "FAILED" -> "Xatolik / Ulanmadi";
            case "CARRIER_REJECTED" -> "Operator rad etdi";
            case "REFUSED" -> "Rad etildi";
            case "HUNG_UP" -> "Mijoz go'shakni qo'ydi";
            case "TRANSFERRED" -> "Operatorga uzatildi";
            case "WRONG_NUMBER" -> "Noto'g'ri raqam";
            case "DO_NOT_CALL" -> "Qora ro'yxat (DNC)";
            case "CALLBACK_REQUESTED" -> "Qayta qo'ng'iroq so'raldi";
            default -> code;
        };
    }

    private static String mapDispositionColor(String code) {
        if (code == null) return "var(--neutral-400, #94a3b8)";
        return switch (code) {
            case "COMPLETED", "PROMISE_TO_PAY" -> "var(--success, #10b981)";
            case "NO_ANSWER", "WRONG_NUMBER" -> "var(--neutral-400, #94a3b8)";
            case "VOICEMAIL", "BUSY_AMD", "REFUSED", "HUNG_UP" -> "var(--warning, #f59e0b)";
            case "FAILED", "CARRIER_REJECTED", "DO_NOT_CALL" -> "var(--danger, #ef4444)";
            case "TRANSFERRED", "CALLBACK_REQUESTED" -> "var(--info, #3b82f6)";
            default -> "var(--neutral-400, #94a3b8)";
        };
    }

    private static DirectionMix mapDirectionMix(List<DashboardDirectionRow> rows, long totalCalls) {
        DashboardDirectionRow outRow = null;
        DashboardDirectionRow inRow = null;
        for (DashboardDirectionRow r : rows) {
            if ("inbound".equalsIgnoreCase(r.direction())) {
                inRow = r;
            } else {
                outRow = r;
            }
        }
        DirectionStats outbound = toDirectionStats(outRow, totalCalls);
        DirectionStats inbound = toDirectionStats(inRow, totalCalls);
        return new DirectionMix(outbound, inbound);
    }

    private static DirectionStats toDirectionStats(DashboardDirectionRow r, long totalCalls) {
        if (r == null) {
            return new DirectionStats(0, 0, 0, 0, 0.0);
        }
        int pct = totalCalls > 0 ? (int) Math.round((double) r.totalCalls() * 100 / totalCalls) : 0;
        double successRate = r.totalCalls() > 0 ? round1(r.completedCalls() * 100.0 / r.totalCalls()) : 0.0;
        return new DirectionStats(r.totalCalls(), pct, r.totalMinutes(), r.avgDurationSec(), successRate);
    }

    private static List<TopAgentItem> mapTopAgents(List<DashboardAgentRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        long maxCalls = rows.get(0).calls();
        return rows.stream().map(a -> {
            int relativePct = maxCalls > 0 ? (int) Math.round((double) a.calls() * 100 / maxCalls) : 0;
            String role = "operator".equalsIgnoreCase(a.type()) ? "Operator" : "Avtomatlashgan robot";
            return new TopAgentItem(a.id(), a.name(), role, a.type(), a.calls(), a.minutes(), a.successRate(), relativePct);
        }).toList();
    }

    private static double rate(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    private static double orZero(Double value) {
        return value != null ? value : 0.0;
    }
}
