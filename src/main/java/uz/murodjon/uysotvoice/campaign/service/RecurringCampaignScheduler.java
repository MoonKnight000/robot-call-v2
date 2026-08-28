package uz.murodjon.uysotvoice.campaign.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.campaign.dto.Campaign;
import uz.murodjon.uysotvoice.campaign.enums.CampaignStatus;
import uz.murodjon.uysotvoice.campaign.enums.RecurrenceType;
import uz.murodjon.uysotvoice.campaign.repository.CampaignRepository;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Background scheduler that periodically inspects recurring campaigns and activates
 * them according to their recurrence schedule (DAILY, WEEKLY, MONTHLY, CRON).
 */
@Component
public class RecurringCampaignScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecurringCampaignScheduler.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Tashkent");

    private final CampaignRepository campaigns;
    private final CampaignService campaignService;
    private final Clock clock;
    private final boolean enabled;

    public RecurringCampaignScheduler(
            CampaignRepository campaigns,
            CampaignService campaignService,
            Clock clock,
            @Value("${voice-agent.campaign.recurrence-enabled:true}") boolean enabled
    ) {
        this.campaigns = campaigns;
        this.campaignService = campaignService;
        this.clock = clock;
        this.enabled = enabled;
    }

    /**
     * Inspects all non-ONCE recurring campaigns every minute (or configured interval).
     */
    @Scheduled(fixedDelayString = "${voice-agent.campaign.recurrence-check-ms:60000}")
    public void sweepRecurringCampaigns() {
        if (!enabled) {
            return;
        }

        ZonedDateTime now = ZonedDateTime.now(clock.withZone(ZONE));
        List<Campaign> recurring = campaigns.findRecurring();

        for (Campaign c : recurring) {
            try {
                if (isDue(c, now)) {
                    log.info("Recurring campaign {} ({}) is due. Triggering execution...", c.id(), c.name());
                    campaignService.triggerRecurrenceRun(c.id(), c.autoResetTargets());
                }
            } catch (Exception e) {
                log.error("Failed to evaluate recurrence for campaign {}: {}", c.id(), e.getMessage(), e);
            }
        }
    }

    /**
     * Determines whether a recurring campaign is due for activation at the given time.
     */
    public boolean isDue(Campaign c, ZonedDateTime now) {
        if (c.recurrenceType() == null || c.recurrenceType() == RecurrenceType.ONCE) {
            return false;
        }

        // If campaign is already active, no need to trigger again until it completes/pauses
        if (c.status() == CampaignStatus.ACTIVE) {
            return false;
        }

        LocalDate today = now.toLocalDate();
        LocalTime time = now.toLocalTime();

        // 1. Check dial window
        LocalTime windowStart = c.dialWindowStart() != null ? c.dialWindowStart() : LocalTime.MIN;
        LocalTime windowEnd = c.dialWindowEnd() != null ? c.dialWindowEnd() : LocalTime.MAX;
        if (time.isBefore(windowStart) || time.isAfter(windowEnd)) {
            return false;
        }

        // 2. Check allowed weekdays (dialDays)
        DayOfWeek currentDayOfWeek = now.getDayOfWeek();
        if (!c.allowedDays().contains(currentDayOfWeek)) {
            return false;
        }

        // 3. Check if already ran today
        if (c.lastRunAt() != null) {
            LocalDate lastRunDate = c.lastRunAt().atZone(ZONE).toLocalDate();
            if (lastRunDate.isEqual(today)) {
                return false;
            }
        }

        // 4. Recurrence frequency match
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
            return today.getDayOfMonth() == 1; // Default to 1st of month
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
