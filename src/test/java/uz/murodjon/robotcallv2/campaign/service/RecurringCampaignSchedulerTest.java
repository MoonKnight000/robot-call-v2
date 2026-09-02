package uz.murodjon.robotcallv2.campaign.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.campaign.application.service.CampaignService;
import uz.murodjon.robotcallv2.campaign.application.service.RecurringCampaignScheduler;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignType;
import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecurringCampaignSchedulerTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Tashkent");

    private CampaignRepository campaigns;
    private CampaignService campaignService;
    private RecurringCampaignScheduler scheduler;

    @BeforeEach
    void setUp() {
        campaigns = mock(CampaignRepository.class);
        campaignService = mock(CampaignService.class);
    }

    private Campaign buildCampaign(
            long id,
            RecurrenceType recurrenceType,
            Integer recurringDayOfMonth,
            String cronExpression,
            Set<DayOfWeek> dialDays,
            LocalTime windowStart,
            LocalTime windowEnd,
            CampaignStatus status
    ) {
        return new Campaign(
                id, "Test Recurring", CampaignType.DEBT_COLLECTION, status, "goal", "uz-UZ",
                windowStart, windowEnd, dialDays, 3, 0, 10, null, 0, 1L, 1L, true, 1L,
                recurrenceType, recurringDayOfMonth, cronExpression, true, null
        );
    }

    @Test
    void dailyCampaignTriggersWithinDialWindow() {
        // Wednesday 2026-07-01 at 10:00
        ZonedDateTime now = ZonedDateTime.of(LocalDate.of(2026, 7, 1), LocalTime.of(10, 0), ZONE);
        scheduler = new RecurringCampaignScheduler(campaigns, campaignService, Clock.fixed(now.toInstant(), ZONE), true);

        Campaign daily = buildCampaign(1L, RecurrenceType.DAILY, null, null,
                Set.of(DayOfWeek.WEDNESDAY), LocalTime.of(9, 0), LocalTime.of(18, 0), CampaignStatus.DRAFT);

        assertThat(scheduler.isDue(daily, now)).isTrue();
    }

    @Test
    void dailyCampaignDoesNotTriggerOutsideDialWindow() {
        // Wednesday 2026-07-01 at 20:00 (outside 09:00-18:00)
        ZonedDateTime now = ZonedDateTime.of(LocalDate.of(2026, 7, 1), LocalTime.of(20, 0), ZONE);
        scheduler = new RecurringCampaignScheduler(campaigns, campaignService, Clock.fixed(now.toInstant(), ZONE), true);

        Campaign daily = buildCampaign(1L, RecurrenceType.DAILY, null, null,
                Set.of(DayOfWeek.WEDNESDAY), LocalTime.of(9, 0), LocalTime.of(18, 0), CampaignStatus.DRAFT);

        assertThat(scheduler.isDue(daily, now)).isFalse();
    }

    @Test
    void weeklyCampaignTriggersOnlyOnAllowedDay() {
        // Sunday 2026-07-05 at 11:00
        ZonedDateTime sunday = ZonedDateTime.of(LocalDate.of(2026, 7, 5), LocalTime.of(11, 0), ZONE);
        scheduler = new RecurringCampaignScheduler(campaigns, campaignService, Clock.fixed(sunday.toInstant(), ZONE), true);

        Campaign sundayCampaign = buildCampaign(2L, RecurrenceType.WEEKLY, null, null,
                Set.of(DayOfWeek.SUNDAY), LocalTime.of(9, 0), LocalTime.of(18, 0), CampaignStatus.COMPLETED);

        assertThat(scheduler.isDue(sundayCampaign, sunday)).isTrue();

        // Monday 2026-07-06 at 11:00 -> should not trigger
        ZonedDateTime monday = sunday.plusDays(1);
        assertThat(scheduler.isDue(sundayCampaign, monday)).isFalse();
    }

    @Test
    void monthlyCampaignTriggersOnSpecifiedDayOfMonth() {
        // 1st of July 2026 at 10:00
        ZonedDateTime firstOfMonth = ZonedDateTime.of(LocalDate.of(2026, 7, 1), LocalTime.of(10, 0), ZONE);
        scheduler = new RecurringCampaignScheduler(campaigns, campaignService, Clock.fixed(firstOfMonth.toInstant(), ZONE), true);

        Campaign monthly = buildCampaign(3L, RecurrenceType.MONTHLY, 1, null,
                Set.of(DayOfWeek.WEDNESDAY), LocalTime.of(9, 0), LocalTime.of(18, 0), CampaignStatus.DRAFT);

        assertThat(scheduler.isDue(monthly, firstOfMonth)).isTrue();

        // 2nd of July -> should not trigger
        ZonedDateTime secondOfMonth = firstOfMonth.plusDays(1);
        assertThat(scheduler.isDue(monthly, secondOfMonth)).isFalse();
    }

    @Test
    void sweepRecurringCampaignsExecutesDueCampaigns() {
        ZonedDateTime now = ZonedDateTime.of(LocalDate.of(2026, 7, 1), LocalTime.of(10, 0), ZONE);
        scheduler = new RecurringCampaignScheduler(campaigns, campaignService, Clock.fixed(now.toInstant(), ZONE), true);

        Campaign dueCampaign = buildCampaign(10L, RecurrenceType.DAILY, null, null,
                Set.of(DayOfWeek.WEDNESDAY), LocalTime.of(9, 0), LocalTime.of(18, 0), CampaignStatus.DRAFT);

        when(campaigns.findRecurring()).thenReturn(List.of(dueCampaign));

        scheduler.sweepRecurringCampaigns();

        verify(campaignService).triggerRecurrenceRun(10L, true);
    }
}
