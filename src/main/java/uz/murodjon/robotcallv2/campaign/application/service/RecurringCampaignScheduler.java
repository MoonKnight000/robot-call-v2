package uz.murodjon.robotcallv2.campaign.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;

import java.time.*;
import java.util.List;

/**
 * Background scheduler that periodically inspects recurring campaigns and activates
 * them according to their recurrence schedule (DAILY, WEEKLY, MONTHLY, CRON).
 */
@Component
public class RecurringCampaignScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecurringCampaignScheduler.class);

    private final CampaignRepository repository;
    private final CampaignUseCase campaignUseCase;
    private final CompanyConfigUseCase companyConfig;
    /** One sweep at a time across the cluster — two at once would reset the same list twice. */
    private final ClusterJobLock clusterJobLock;
    private final Clock clock;
    private final boolean enabled;

    public RecurringCampaignScheduler(
            CampaignRepository repository,
            CampaignUseCase campaignUseCase,
            CompanyConfigUseCase companyConfig,
            ClusterJobLock clusterJobLock,
            Clock clock,
            @Value("${voice-agent.campaign.recurrence-enabled:true}") boolean enabled
    ) {
        this.repository = repository;
        this.campaignUseCase = campaignUseCase;
        this.companyConfig = companyConfig;
        this.clusterJobLock = clusterJobLock;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${voice-agent.campaign.recurrence-check-ms:60000}")
    public void sweepRecurringCampaigns() {
        if (!enabled) {
            return;
        }
        clusterJobLock.runExclusively("campaign.recurrence-sweep", this::sweepDueCampaigns);
    }

    private void sweepDueCampaigns() {
        // Same minute tick, same lock: startAt/endAt moments are honoured here too.
        try {
            campaignUseCase.sweepScheduledCampaigns();
        } catch (Exception e) {
            log.error("Scheduled campaign sweep failed: {}", e.getMessage(), e);
        }

        List<Campaign> recurring = repository.findRecurring();

        for (Campaign c : recurring) {
            try {
                // "Every day at 09:00" is 09:00 on the company's clock, not the server's.
                CompanyConfig config = companyConfig.find(c.companyId());
                ZonedDateTime now = ZonedDateTime.now(config != null ? clock.withZone(config.zoneId()) : clock);
                if (isDue(c, now)) {
                    log.info("Recurring campaign {} ({}) is due. Triggering execution...", c.id(), c.name());
                    campaignUseCase.triggerRecurrenceRun(c.companyId(), c.id(), c.autoResetTargets());
                }
            } catch (Exception e) {
                log.error("Failed to evaluate recurrence for campaign {}: {}", c.id(), e.getMessage(), e);
            }
        }
    }

    public boolean isDue(Campaign c, ZonedDateTime now) {
        if (c.recurrenceType() == null || c.recurrenceType() == RecurrenceType.ONCE) {
            return false;
        }

        // Only a finished run starts the next one. PAUSED is someone's decision — an operator
        // after a complaint, or the platform after a dead trunk or a spent budget — a DRAFT is
        // not ready yet, and SCHEDULED has a start of its own.
        if (c.status() != CampaignStatus.COMPLETED) {
            return false;
        }

        LocalDate today = now.toLocalDate();
        LocalTime time = now.toLocalTime();

        LocalTime windowStart = c.dialWindowStart() != null ? c.dialWindowStart() : LocalTime.MIN;
        LocalTime windowEnd = c.dialWindowEnd() != null ? c.dialWindowEnd() : LocalTime.MAX;
        if (time.isBefore(windowStart) || time.isAfter(windowEnd)) {
            return false;
        }

        DayOfWeek currentDayOfWeek = now.getDayOfWeek();
        if (!c.allowedDays().contains(currentDayOfWeek)) {
            return false;
        }

        if (c.lastRunAt() != null) {
            LocalDate lastRunDate = c.lastRunAt().atZone(now.getZone()).toLocalDate();
            if (lastRunDate.isEqual(today)) {
                return false;
            }
        }

        return switch (c.recurrenceType()) {
            case DAILY -> true;
            case WEEKLY -> c.allowedDays().contains(currentDayOfWeek);
            case MONTHLY -> matchesMonthly(c, today);
            case CRON -> matchesCron(c, now);
            default -> false;
        };
    }

    private boolean matchesMonthly(Campaign c, LocalDate today) {
        if (c.recurringDayOfMonth() == null) {
            return today.getDayOfMonth() == 1;
        }
        int targetDay = c.recurringDayOfMonth();
        int maxDaysInMonth = today.lengthOfMonth();
        int effectiveDay = Math.min(targetDay, maxDaysInMonth);
        return today.getDayOfMonth() == effectiveDay;
    }

    private boolean matchesCron(Campaign c, ZonedDateTime now) {
        if (c.cronExpression() == null || c.cronExpression().isBlank()) {
            return false;
        }
        try {
            CronExpression cron = CronExpression.parse(c.cronExpression().trim());
            ZonedDateTime lastCheck = now.minusMinutes(1);
            ZonedDateTime next = cron.next(lastCheck);
            return next != null && !next.isAfter(now);
        } catch (Exception e) {
            log.warn("Invalid cron expression for campaign {}: {}", c.id(), c.cronExpression());
            return false;
        }
    }
}
