package uz.murodjon.robotcallv2.report.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.report.application.dto.CreateReportScheduleRequest;
import uz.murodjon.robotcallv2.report.application.port.input.DashboardReportUseCase;
import uz.murodjon.robotcallv2.report.application.port.input.ReportExportUseCase;
import uz.murodjon.robotcallv2.report.application.port.input.ReportScheduleUseCase;
import uz.murodjon.robotcallv2.report.application.port.output.ReportScheduleRepository;
import uz.murodjon.robotcallv2.report.domain.entity.ReportSchedule;
import uz.murodjon.robotcallv2.report.domain.entity.ReportScheduleFilter;
import uz.murodjon.robotcallv2.report.domain.entity.ReportSummary;
import uz.murodjon.robotcallv2.report.domain.enums.ReportPeriodicity;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class ReportScheduleService implements ReportScheduleUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReportScheduleService.class);
    private static final Set<String> ALLOWED_FORMATS = Set.of("csv", "pdf", "xlsx");

    private final ReportScheduleRepository reportScheduleRepository;
    private final CampaignUseCase campaignUseCase;
    private final DashboardReportUseCase dashboardReportUseCase;
    private final ReportExportUseCase reportExportUseCase;
    private final ReportEmailSender emailSender;
    private final AuditUseCase audit;
    private final NotificationUseCase notifications;
    private final ClusterJobLock clusterJobLock;
    private final boolean enabled;

    public ReportScheduleService(ReportScheduleRepository reportScheduleRepository, CampaignUseCase campaignUseCase,
                                 DashboardReportUseCase dashboardReportUseCase, ReportExportUseCase reportExportUseCase,
                                 ReportEmailSender emailSender, AuditUseCase audit,
                                 NotificationUseCase notifications, ClusterJobLock clusterJobLock,
                                 @Value("${voice-agent.report-schedule.enabled:false}") boolean enabled) {
        this.clusterJobLock = clusterJobLock;
        this.reportScheduleRepository = reportScheduleRepository;
        this.campaignUseCase = campaignUseCase;
        this.dashboardReportUseCase = dashboardReportUseCase;
        this.reportExportUseCase = reportExportUseCase;
        this.emailSender = emailSender;
        this.audit = audit;
        this.notifications = notifications;
        this.enabled = enabled;
    }

    @Override
    public ReportSchedule create(long companyId, CreateReportScheduleRequest request) {
        if (request.format() != null && !request.format().isBlank() && !ALLOWED_FORMATS.contains(request.format().toLowerCase())) {
            throw new ValidationException(ErrorCode.REPORT_SCHEDULE_FORMAT_INVALID, ALLOWED_FORMATS, request.format());
        }
        if (request.campaignId() != null) {
            campaignUseCase.requireCampaign(companyId, request.campaignId());
        }
        String format = request.format() != null && !request.format().isBlank() ? request.format().toLowerCase() : "pdf";
        long id = reportScheduleRepository.create(companyId, ReportSchedule.requested(request.email(), request.periodicity(), format, request.campaignId()));
        audit.record(companyId, "REPORT_SCHEDULE_CREATE", "report_schedule", String.valueOf(id),
                request.email() + " (" + request.periodicity() + ")");
        return requireSchedule(companyId, id);
    }

    @Override
    public PageableData<ReportSchedule> list(long companyId, ReportScheduleFilter filter) {
        List<ReportSchedule> rows = reportScheduleRepository.findAll(companyId, filter);
        long total = reportScheduleRepository.count(companyId, filter);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    @Override
    public ReportSchedule requireSchedule(long companyId, long id) {
        ReportSchedule row = reportScheduleRepository.find(companyId, id);
        if (row == null) {
            throw new NotFoundException(ErrorCode.REPORT_SCHEDULE_NOT_FOUND, id);
        }
        return row;
    }

    @Override
    public ReportSchedule disable(long companyId, long id) {
        requireSchedule(companyId, id);
        reportScheduleRepository.disable(companyId, id);
        audit.record(companyId, "REPORT_SCHEDULE_DISABLE", "report_schedule", String.valueOf(id), null);
        return requireSchedule(companyId, id);
    }

    @Override
    @Scheduled(fixedDelayString = "#{${voice-agent.report-schedule.check-minutes:60} * 60 * 1000}")
    public void sendDue() {
        if (!enabled) {
            return;
        }
        // One instance at a time, so a schedule is marked sent before another looks at it.
        clusterJobLock.runExclusively("report.send-due", this::sendDueSchedules);
    }

    private void sendDueSchedules() {
        Instant now = Instant.now();
        for (ReportSchedule schedule : reportScheduleRepository.findEnabled()) {
            try {
                Instant since = schedule.lastSentAt() != null ? schedule.lastSentAt() : schedule.createdAt();
                if (since.plus(schedule.periodicity().span()).isAfter(now)) {
                    continue;
                }
                sendOne(schedule, since, now);
                reportScheduleRepository.markSent(schedule.id(), now);
            } catch (Exception e) {
                log.warn("Report schedule {} failed: {}", schedule.id(), e.getMessage());
            }
        }
    }

    private void sendOne(ReportSchedule schedule, Instant from, Instant to) throws Exception {
        ReportSummary summary = dashboardReportUseCase.findSummary(schedule.companyId(), from.toString(), to.toString(),
                schedule.campaignId());
        byte[] rendered = reportExportUseCase.renderSummaryBytes(schedule.format(), summary);
        String contentType = reportExportUseCase.resolveContentType(schedule.format());
        emailSender.send(schedule.email(),
                "Uysot Voice — hisobot (" + schedule.periodicity() + ")",
                "Ilova qilingan fayl " + from + " dan " + to + " gacha bo'lgan davrni qamrab oladi.",
                rendered, "report." + schedule.format(), contentType);
        audit.record(schedule.companyId(), "REPORT_SCHEDULE_SENT", "report_schedule",
                String.valueOf(schedule.id()), schedule.email());
        if (schedule.periodicity() == ReportPeriodicity.DAILY) {
            notifications.notify(schedule.companyId(), NotificationType.DAILY_REPORT,
                    "Kunlik hisobot tayyor", schedule.email() + " manziliga yuborildi", null);
        }
    }
}
