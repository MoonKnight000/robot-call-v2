package uz.murodjon.robotcallv2.report.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.campaign.application.service.CampaignService;
import uz.murodjon.robotcallv2.notification.application.service.NotificationService;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.report.application.dto.CreateReportScheduleRequest;
import uz.murodjon.robotcallv2.report.application.dto.ReportScheduleFilter;
import uz.murodjon.robotcallv2.report.application.port.input.ReportScheduleUseCase;
import uz.murodjon.robotcallv2.report.application.port.output.ReportScheduleRepository;
import uz.murodjon.robotcallv2.report.domain.entity.ReportSchedule;
import uz.murodjon.robotcallv2.report.domain.entity.ReportSummary;
import uz.murodjon.robotcallv2.report.domain.enums.ReportPeriodicity;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class ReportScheduleService implements ReportScheduleUseCase {

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

    @Override
    public ReportSchedule create(CreateReportScheduleRequest r) {
        if (r.format() != null && !r.format().isBlank() && !ALLOWED_FORMATS.contains(r.format().toLowerCase())) {
            throw new ValidationException(ErrorCode.REPORT_SCHEDULE_FORMAT_INVALID, ALLOWED_FORMATS, r.format());
        }
        if (r.campaignId() != null) {
            campaigns.requireCampaign(r.campaignId());
        }
        long id = schedules.create(r);
        audit.record("REPORT_SCHEDULE_CREATE", "report_schedule", String.valueOf(id),
                r.email() + " (" + r.periodicity() + ")");
        return requireSchedule(id);
    }

    @Override
    public PageableData<ReportSchedule> list(ReportScheduleFilter filter) {
        List<ReportSchedule> rows = schedules.findAll(filter);
        long total = schedules.count(filter);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    @Override
    public ReportSchedule requireSchedule(long id) {
        ReportSchedule row = schedules.find(id);
        if (row == null) {
            throw new NotFoundException(ErrorCode.REPORT_SCHEDULE_NOT_FOUND, id);
        }
        return row;
    }

    @Override
    public ReportSchedule disable(long id) {
        requireSchedule(id);
        schedules.disable(id);
        audit.record("REPORT_SCHEDULE_DISABLE", "report_schedule", String.valueOf(id), null);
        return requireSchedule(id);
    }

    @Override
    @Scheduled(fixedDelayString = "#{${voice-agent.report-schedule.check-minutes:60} * 60 * 1000}")
    public void sendDue() {
        if (!enabled) {
            return;
        }
        Instant now = Instant.now();
        for (ReportSchedule schedule : schedules.findEnabled()) {
            try {
                Instant since = schedule.lastSentAt() != null ? schedule.lastSentAt() : schedule.createdAt();
                if (since.plus(schedule.periodicity().span()).isAfter(now)) {
                    continue;
                }
                sendOne(schedule, since, now);
                schedules.markSent(schedule.id(), now);
            } catch (Exception e) {
                log.warn("Report schedule {} failed: {}", schedule.id(), e.getMessage());
            }
        }
    }

    private void sendOne(ReportSchedule schedule, Instant from, Instant to) throws Exception {
        ReportSummary summary = reportService.summary(from.toString(), to.toString(), schedule.campaignId());
        byte[] rendered = exportFactory.renderBytes(schedule.format(), summary);
        String contentType = exportFactory.contentType(schedule.format());
        emailSender.send(schedule.email(),
                "Uysot Voice — hisobot (" + schedule.periodicity() + ")",
                "Ilova qilingan fayl " + from + " dan " + to + " gacha bo'lgan davrni qamrab oladi.",
                rendered, "report." + schedule.format(), contentType);
        audit.record("REPORT_SCHEDULE_SENT", "report_schedule", String.valueOf(schedule.id()), schedule.email());
        if (schedule.periodicity() == ReportPeriodicity.DAILY) {
            notifications.notify(schedule.companyId(), NotificationType.DAILY_REPORT,
                    "Kunlik hisobot tayyor", schedule.email() + " manziliga yuborildi", null);
        }
    }
}
