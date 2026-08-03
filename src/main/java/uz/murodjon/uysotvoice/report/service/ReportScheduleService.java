package uz.murodjon.uysotvoice.report.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.campaign.service.CampaignService;
import uz.murodjon.uysotvoice.notification.enums.NotificationType;
import uz.murodjon.uysotvoice.notification.service.NotificationService;
import uz.murodjon.uysotvoice.report.controller.ReportExportFactory;
import uz.murodjon.uysotvoice.report.dto.CreateReportScheduleRequest;
import uz.murodjon.uysotvoice.report.dto.ReportSchedule;
import uz.murodjon.uysotvoice.report.dto.ReportScheduleFilter;
import uz.murodjon.uysotvoice.report.dto.ReportSummary;
import uz.murodjon.uysotvoice.report.entity.ReportScheduleEntity;
import uz.murodjon.uysotvoice.report.enums.ReportPeriodicity;
import uz.murodjon.uysotvoice.report.repository.ReportScheduleRepository;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.exception.NotFoundException;
import uz.murodjon.uysotvoice.shared.exception.ValidationException;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * "Jadval bo'yicha yuborish" (§10.10): CRUD for {@code report_schedule}, plus the sweep
 * that actually emails a due schedule's report. Kept apart from {@link ReportService}
 * (which only ever reads and returns data within one request) since this owns a
 * recurring background job and an outbound side effect (SMTP) neither of that
 * service's other callers expect.
 */
@Service
public class ReportScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ReportScheduleService.class);
    private static final Set<String> ALLOWED_FORMATS = Set.of("csv", "pdf", "xlsx");

    private final ReportScheduleRepository schedules;
    private final CampaignService campaigns;
    private final ReportService reportService;
    private final ReportExportFactory exportFactory;
    private final ReportEmailSender emailSender;
    private final AuditService audit;
    private final NotificationService notifications;
    private final boolean enabled;

    public ReportScheduleService(ReportScheduleRepository schedules, CampaignService campaigns,
                                 ReportService reportService, ReportExportFactory exportFactory,
                                 ReportEmailSender emailSender, AuditService audit,
                                 NotificationService notifications,
                                 @Value("${voice-agent.report-schedule.enabled:false}") boolean enabled) {
        this.schedules = schedules;
        this.campaigns = campaigns;
        this.reportService = reportService;
        this.exportFactory = exportFactory;
        this.emailSender = emailSender;
        this.audit = audit;
        this.notifications = notifications;
        this.enabled = enabled;
    }

    public ReportSchedule create(CreateReportScheduleRequest r) {
        if (r.format() != null && !r.format().isBlank() && !ALLOWED_FORMATS.contains(r.format().toLowerCase())) {
            throw new ValidationException("format must be one of " + ALLOWED_FORMATS + ", got '" + r.format() + "'");
        }
        if (r.campaignId() != null) {
            campaigns.requireCampaign(r.campaignId()); // 404s if unknown or another company's
        }
        long id = schedules.create(r);
        audit.record("REPORT_SCHEDULE_CREATE", "report_schedule", String.valueOf(id),
                r.email() + " (" + r.periodicity() + ")");
        return requireSchedule(id);
    }

    public PageableData<ReportSchedule> list(ReportScheduleFilter filter) {
        List<ReportSchedule> rows = schedules.findAll(filter);
        long total = schedules.count(filter);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    public ReportSchedule requireSchedule(long id) {
        ReportSchedule row = schedules.find(id);
        if (row == null) {
            throw new NotFoundException("report_schedule", id);
        }
        return row;
    }

    /** Cancel a schedule — soft-delete (mirrors campaign archive / inbound-route disable). */
    public ReportSchedule disable(long id) {
        requireSchedule(id);
        schedules.disable(id);
        audit.record("REPORT_SCHEDULE_DISABLE", "report_schedule", String.valueOf(id), null);
        return requireSchedule(id);
    }

    /**
     * Every enabled schedule whose period has elapsed since it last sent (or since it
     * was created, if it never has), rendered and emailed. One schedule's failure (bad
     * address, SMTP hiccup) is logged and skipped rather than aborting the sweep — the
     * same best-effort convention {@code CallRecordService} uses for its own writes.
     */
    @Scheduled(fixedDelayString = "#{${voice-agent.report-schedule.check-minutes:60} * 60 * 1000}")
    public void sendDue() {
        if (!enabled) {
            return;
        }
        Instant now = Instant.now();
        for (ReportScheduleEntity schedule : schedules.findEnabled()) {
            try {
                Instant since = schedule.getLastSentAt() != null ? schedule.getLastSentAt() : schedule.getCreatedAt();
                if (since.plus(schedule.getPeriodicity().span()).isAfter(now)) {
                    continue; // not due yet
                }
                sendOne(schedule, since, now);
                schedules.markSent(schedule.getId(), now);
            } catch (Exception e) {
                log.warn("Report schedule {} failed: {}", schedule.getId(), e.getMessage());
            }
        }
    }

    private void sendOne(ReportScheduleEntity schedule, Instant from, Instant to) throws Exception {
        ReportSummary summary = reportService.summary(from.toString(), to.toString(), schedule.getCampaignId());
        ResponseEntity<byte[]> rendered = exportFactory.toResponse(schedule.getFormat(), summary);
        MediaType contentType = rendered.getHeaders().getContentType();
        emailSender.send(schedule.getEmail(),
                "Uysot Voice — hisobot (" + schedule.getPeriodicity() + ")",
                "Ilova qilingan fayl " + from + " dan " + to + " gacha bo'lgan davrni qamrab oladi.",
                rendered.getBody(), "report." + schedule.getFormat(),
                contentType != null ? contentType.toString() : "application/octet-stream");
        audit.record("REPORT_SCHEDULE_SENT", "report_schedule", String.valueOf(schedule.getId()),
                schedule.getEmail());
        if (schedule.getPeriodicity() == ReportPeriodicity.DAILY) {
            notifications.notify(schedule.getCompanyId(), NotificationType.DAILY_REPORT,
                    "Kunlik hisobot tayyor", schedule.getEmail() + " manziliga yuborildi", null);
        }
    }
}
