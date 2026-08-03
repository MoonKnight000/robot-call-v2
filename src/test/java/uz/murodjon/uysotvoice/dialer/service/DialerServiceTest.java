package uz.murodjon.uysotvoice.dialer.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import uz.murodjon.uysotvoice.agent.ari.AriService;
import uz.murodjon.uysotvoice.agent.lifecycle.GracefulShutdownManager;
import uz.murodjon.uysotvoice.campaign.dto.Campaign;
import uz.murodjon.uysotvoice.campaign.dto.CampaignTarget;
import uz.murodjon.uysotvoice.campaign.enums.CampaignStatus;
import uz.murodjon.uysotvoice.campaign.enums.CampaignType;
import uz.murodjon.uysotvoice.campaign.enums.TargetStatus;
import uz.murodjon.uysotvoice.campaign.repository.CampaignRepository;
import uz.murodjon.uysotvoice.campaign.repository.CampaignTargetRepository;
import uz.murodjon.uysotvoice.campaign.service.CampaignService;
import uz.murodjon.uysotvoice.company.service.CompanyConfigService;
import uz.murodjon.uysotvoice.dialer.config.DialerProperties;
import uz.murodjon.uysotvoice.dialer.config.RabbitConfig;
import uz.murodjon.uysotvoice.dialer.config.RetryProperties;
import uz.murodjon.uysotvoice.dialer.dto.CallTask;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Dispatch decides when a real phone rings, so the two constraints that keep it lawful and
 * affordable — the §11.2 dial window and the per-campaign daily cap — are checked directly
 * against a fixed clock rather than by waiting for the right hour to come round.
 */
class DialerServiceTest {

    private static final long CAMPAIGN_ID = 3L;
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private CampaignRepository campaigns;
    private CampaignTargetRepository targets;
    private RabbitTemplate rabbit;
    private DialerState state;

    @BeforeEach
    void setUp() {
        campaigns = mock(CampaignRepository.class);
        targets = mock(CampaignTargetRepository.class);
        rabbit = mock(RabbitTemplate.class);
        state = mock(DialerState.class);
        when(state.active()).thenReturn(0);
    }

    /** A dialer whose "now" is 2026-07-01 (a Wednesday) at {@code hour}. */
    private DialerService dialerAt(int hour) {
        ZonedDateTime now = ZonedDateTime.of(LocalDate.of(2026, 7, 1), LocalTime.of(hour, 0), ZONE);
        return new DialerService(
                new DialerProperties(true, 5, 10, 5, 60,
                        new RetryProperties(180, 15, 1200, true)),
                // A mock with no stubbed find() returns null, i.e. "no company config yet" —
                // withinWindow() treats that as no additional ceiling, matching pre-B.3 behaviour.
                campaigns, targets, mock(CampaignService.class), mock(CompanyConfigService.class), rabbit, state,
                mock(OutboundCallRegistry.class), mock(AriService.class),
                mock(GracefulShutdownManager.class),
                Clock.fixed(now.toInstant(), ZONE));
    }

    private void givenActiveCampaign(int dailyCallCap, Set<DayOfWeek> dialDays) {
        when(campaigns.findActive()).thenReturn(List.of(new Campaign(
                CAMPAIGN_ID, "c", CampaignType.DEBT_COLLECTION, CampaignStatus.ACTIVE, "goal", "uz-UZ",
                LocalTime.of(9, 0), LocalTime.of(20, 0), dialDays, 3, 24, 5, null, dailyCallCap, 1L, 1L, true, null)));
    }

    private void givenDueTargets(int count) {
        when(targets.claimDue(anyLong(), anyInt())).thenAnswer(call -> {
            int limit = call.getArgument(1);
            return java.util.stream.IntStream.range(0, Math.min(count, limit))
                    .mapToObj(i -> new CampaignTarget(100L + i, CAMPAIGN_ID, 1L, "99890111223" + i,
                            "uz-UZ", "{}", TargetStatus.PENDING, 0, false))
                    .toList();
        });
    }

    @Test
    void dispatchesInsideTheWindow() {
        givenActiveCampaign(0, Set.of(DayOfWeek.WEDNESDAY));
        givenDueTargets(2);

        dialerAt(10).dispatch();

        verify(rabbit, times(2)).convertAndSend(eq(RabbitConfig.CALL_TASK_QUEUE), any(CallTask.class));
    }

    @Test
    void dispatchesNothingBeforeTheWindowOpens() {
        givenActiveCampaign(0, Set.of(DayOfWeek.WEDNESDAY));
        givenDueTargets(2);

        dialerAt(7).dispatch();

        verify(targets, never()).claimDue(anyLong(), anyInt());
        verify(rabbit, never()).convertAndSend(any(String.class), any(CallTask.class));
    }

    @Test
    void dispatchesNothingOnADayTheCampaignMayNotDial() {
        // §11.2: the time window alone would happily call debtors on a Sunday.
        givenActiveCampaign(0, Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY));
        givenDueTargets(2);

        dialerAt(10).dispatch();

        verify(targets, never()).claimDue(anyLong(), anyInt());
    }

    @Test
    void dailyCapLimitsTheBatch() {
        givenActiveCampaign(5, Set.of(DayOfWeek.WEDNESDAY));
        givenDueTargets(10);
        when(state.dispatchedToday(eq(CAMPAIGN_ID), any(LocalDate.class))).thenReturn(3);

        dialerAt(10).dispatch();

        // 5 allowed, 3 already spent — only 2 more may go out this tick.
        verify(targets).claimDue(CAMPAIGN_ID, 2);
        verify(rabbit, times(2)).convertAndSend(eq(RabbitConfig.CALL_TASK_QUEUE), any(CallTask.class));
    }

    @Test
    void anExhaustedDailyCapStopsTheCampaignWithoutClaimingTargets() {
        // Claiming would mark targets IN_PROGRESS and count an attempt against them, so a
        // capped campaign must stop before touching the queue at all.
        givenActiveCampaign(5, Set.of(DayOfWeek.WEDNESDAY));
        givenDueTargets(10);
        when(state.dispatchedToday(eq(CAMPAIGN_ID), any(LocalDate.class))).thenReturn(5);

        dialerAt(10).dispatch();

        verify(targets, never()).claimDue(anyLong(), anyInt());
        verify(rabbit, never()).convertAndSend(any(String.class), any(CallTask.class));
    }

    @Test
    void noCapMeansTheBatchIsBoundedOnlyByRateAndConcurrency() {
        givenActiveCampaign(0, Set.of(DayOfWeek.WEDNESDAY));
        givenDueTargets(10);

        dialerAt(10).dispatch();

        // min(free concurrency 5, per-campaign share of dispatch-batch 10) = 5.
        verify(targets).claimDue(CAMPAIGN_ID, 5);
    }

    @Test
    void everyDispatchIsCountedAgainstTheDailyCap() {
        givenActiveCampaign(5, Set.of(DayOfWeek.WEDNESDAY));
        givenDueTargets(2);

        dialerAt(10).dispatch();

        verify(state, times(2)).countDispatch(eq(CAMPAIGN_ID), any(LocalDate.class));
        verify(state, times(2)).reserve();
    }

    @Test
    void drainingStopsDispatchEntirely() {
        givenActiveCampaign(0, Set.of(DayOfWeek.WEDNESDAY));
        givenDueTargets(2);
        GracefulShutdownManager shutdown = mock(GracefulShutdownManager.class);
        when(shutdown.isDraining()).thenReturn(true);
        DialerService dialer = new DialerService(
                new DialerProperties(true, 5, 10, 5, 60,
                        new RetryProperties(180, 15, 1200, true)),
                campaigns, targets, mock(CampaignService.class), mock(CompanyConfigService.class), rabbit, state,
                mock(OutboundCallRegistry.class), mock(AriService.class), shutdown,
                Clock.systemDefaultZone());

        dialer.dispatch();

        verify(campaigns, never()).findActive();
    }
}
