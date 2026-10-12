package uz.murodjon.robotcallv2.dialer.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.agent.lifecycle.GracefulShutdownManager;
import uz.murodjon.robotcallv2.agent.rtp.RecordingMode;
import uz.murodjon.robotcallv2.agent.rtp.RtpProperties;
import uz.murodjon.robotcallv2.agent.tts.TtsWarmup;
import uz.murodjon.robotcallv2.aiagent.AiAgentFixtures;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignVariantUseCase;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.enums.*;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.dialer.application.dto.CallTask;
import uz.murodjon.robotcallv2.dialer.infrastructure.config.DialerProperties;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.SipTrunkUseCase;

import java.time.*;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DialerServiceTest {

    private static final long CAMPAIGN_ID = 3L;
    private static final long AI_AGENT_ID = 7L;
    private static final ZoneId ZONE = ZoneId.systemDefault();

    /** 51 even ports — well clear of the limits under test, so only those are exercised. */
    private static final RtpProperties RTP_PROPS =
            new RtpProperties("127.0.0.1", 10000, 10100, "recordings", "", 2, RecordingMode.SPATIAL_STEREO, null, 0, 0);

    private CampaignUseCase campaignUseCase;
    private CampaignTargetUseCase campaignTargetUseCase;
    private SipTrunkUseCase sipTrunks;
    private RabbitTemplate rabbit;
    private DialerState state;
    private TtsWarmup ttsWarmup;
    private AiAgentUseCase aiAgents;
    private CallBillingUseCase callBilling;

    @BeforeEach
    void setUp() {
        campaignUseCase = mock(CampaignUseCase.class);
        campaignTargetUseCase = mock(CampaignTargetUseCase.class);
        sipTrunks = mock(SipTrunkUseCase.class);
        rabbit = mock(RabbitTemplate.class);
        state = mock(DialerState.class);
        ttsWarmup = mock(TtsWarmup.class);
        aiAgents = mock(AiAgentUseCase.class);
        callBilling = mock(CallBillingUseCase.class);
        // Billing is not what these tests are about: every company can pay unless a test
        // says otherwise.
        when(callBilling.hasBalanceForCall(anyLong())).thenReturn(true);
        when(callBilling.reserveForCall(anyLong(), anyLong())).thenReturn(true);
        when(aiAgents.requirePublishedAgent(1L, AI_AGENT_ID))
                .thenReturn(AiAgentFixtures.agent(AI_AGENT_ID, 1L, 10L, "uz-UZ", "dilfuza"));
        when(state.countActive(anyLong())).thenReturn(0);
        when(state.countActiveTotal()).thenReturn(0);
        when(state.countClusterCapacity(anyInt())).thenAnswer(call -> call.getArgument(0));
        when(state.countDispatchedToday(anyLong(), any())).thenReturn(OptionalInt.of(0));
        when(sipTrunks.findTrunksForCall(anyLong(), any())).thenReturn(List.of());
    }

    private DialerService dialerAt(int hour) {
        ZonedDateTime now = ZonedDateTime.of(LocalDate.of(2026, 7, 1), LocalTime.of(hour, 0), ZONE);
        return new DialerService(
                new DialerProperties(true, 5, 10, 5, 5, 60, 0),
                RTP_PROPS, campaignUseCase, campaignTargetUseCase, mock(CompanyConfigUseCase.class),
                sipTrunks, rabbit, state,
                mock(OutboundCallRegistry.class), mock(AriService.class),
                mock(GracefulShutdownManager.class), ttsWarmup,
                Clock.fixed(now.toInstant(), ZONE), mock(CampaignVariantUseCase.class), aiAgents, callBilling,
                mock(CallAttemptUseCase.class), mock(TrunkHealth.class), (jobName, job) -> {
                    job.run();
                    return true;
                }, companyConcurrency());
    }

    /** The company limit from the same properties the dialer gets; no company has its own. */
    private CompanyConcurrencyService companyConcurrency() {
        return new CompanyConcurrencyService(new DialerProperties(true, 5, 10, 5, 5, 60, 0), state,
                mock(CompanyUseCase.class), mock(NotificationUseCase.class), Clock.systemUTC());
    }

    private void givenActiveCampaign(int dailyCallCap, Set<DayOfWeek> dialDays) {
        when(campaignUseCase.findActiveCampaigns()).thenReturn(List.of(Campaign.builder()
                .id(CAMPAIGN_ID)
                .name("c")
                .type(CampaignType.DEBT_COLLECTION)
                .status(CampaignStatus.ACTIVE)
                .dialWindowStart(LocalTime.of(9, 0))
                .dialWindowEnd(LocalTime.of(20, 0))
                .dialDays(dialDays)
                .maxAttempts(3)
                .retryIntervalMinutes(24)
                .maxConcurrentCalls(5)
                .dailyCallCap(dailyCallCap)
                .aiAgentId(AI_AGENT_ID)
                .companyId(1L)
                .recurrenceType(RecurrenceType.ONCE)
                .build()));
    }

    private void givenDueTargets(int count) {
        when(campaignTargetUseCase.claimDueTargets(anyLong(), anyInt())).thenAnswer(call -> {
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

        verify(rabbit, times(2)).convertAndSend(eq(CallTask.QUEUE), any(CallTask.class));
    }

    @Test
    void dispatchesNothingBeforeTheWindowOpens() {
        givenActiveCampaign(0, Set.of(DayOfWeek.WEDNESDAY));
        givenDueTargets(2);

        dialerAt(7).dispatch();

        verify(campaignTargetUseCase, never()).claimDueTargets(anyLong(), anyInt());
        verify(rabbit, never()).convertAndSend(any(String.class), any(CallTask.class));
    }

    @Test
    void dispatchesNothingOnADayTheCampaignMayNotDial() {
        givenActiveCampaign(0, Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY));
        givenDueTargets(2);

        dialerAt(10).dispatch();

        verify(campaignTargetUseCase, never()).claimDueTargets(anyLong(), anyInt());
    }

    @Test
    void dailyCapLimitsTheBatch() {
        givenActiveCampaign(5, Set.of(DayOfWeek.WEDNESDAY));
        givenDueTargets(10);
        when(state.countDispatchedToday(eq(CAMPAIGN_ID), any(LocalDate.class))).thenReturn(OptionalInt.of(3));

        dialerAt(10).dispatch();

        verify(campaignTargetUseCase).claimDueTargets(CAMPAIGN_ID, 2);
        verify(rabbit, times(2)).convertAndSend(eq(CallTask.QUEUE), any(CallTask.class));
    }

    @Test
    void anExhaustedDailyCapStopsTheCampaignWithoutClaimingTargets() {
        givenActiveCampaign(5, Set.of(DayOfWeek.WEDNESDAY));
        givenDueTargets(10);
        when(state.countDispatchedToday(eq(CAMPAIGN_ID), any(LocalDate.class))).thenReturn(OptionalInt.of(5));

        dialerAt(10).dispatch();

        verify(campaignTargetUseCase, never()).claimDueTargets(anyLong(), anyInt());
        verify(rabbit, never()).convertAndSend(any(String.class), any(CallTask.class));
    }

    /** Without today's count the cap cannot be kept, so the campaign waits — it used to dial as if at zero. */
    @Test
    void anUnreadableDailyCountDialsNothing() {
        givenActiveCampaign(5, Set.of(DayOfWeek.WEDNESDAY));
        givenDueTargets(10);
        when(state.countDispatchedToday(eq(CAMPAIGN_ID), any(LocalDate.class))).thenReturn(OptionalInt.empty());

        dialerAt(10).dispatch();

        verify(campaignTargetUseCase, never()).claimDueTargets(anyLong(), anyInt());
        verify(rabbit, never()).convertAndSend(any(String.class), any(CallTask.class));
    }

    @Test
    void noCapMeansTheBatchIsBoundedOnlyByRateAndConcurrency() {
        givenActiveCampaign(0, Set.of(DayOfWeek.WEDNESDAY));
        givenDueTargets(10);

        dialerAt(10).dispatch();

        verify(campaignTargetUseCase).claimDueTargets(CAMPAIGN_ID, 5);
    }

    @Test
    void aScoreCampaignClaimsByScoreAtTheCompanysHour() {
        when(campaignUseCase.findActiveCampaigns()).thenReturn(List.of(Campaign.builder()
                .id(CAMPAIGN_ID)
                .name("c")
                .type(CampaignType.DEBT_COLLECTION)
                .status(CampaignStatus.ACTIVE)
                .dialWindowStart(LocalTime.of(9, 0))
                .dialWindowEnd(LocalTime.of(20, 0))
                .dialDays(Set.of(DayOfWeek.WEDNESDAY))
                .maxAttempts(3)
                .retryIntervalMinutes(24)
                .maxConcurrentCalls(5)
                .aiAgentId(AI_AGENT_ID)
                .companyId(1L)
                .recurrenceType(RecurrenceType.ONCE)
                .targetOrdering(TargetOrdering.SCORE)
                .build()));

        dialerAt(14).dispatch();

        verify(campaignTargetUseCase).claimDueTargetsByScore(CAMPAIGN_ID, 5, 14);
        verify(campaignTargetUseCase, never()).claimDueTargets(anyLong(), anyInt());
    }

    @Test
    void everyDispatchIsCountedAgainstTheDailyCap() {
        givenActiveCampaign(5, Set.of(DayOfWeek.WEDNESDAY));
        givenDueTargets(2);

        dialerAt(10).dispatch();

        verify(state, times(2)).countDispatch(eq(CAMPAIGN_ID), any(LocalDate.class));
        verify(state, times(2)).reserve(anyLong(), anyLong());
    }

    /** The active count is the cluster's, so the ports it is held against are the cluster's too. */
    @Test
    void theClusterMediaCapacityBoundsTheBatch() {
        givenActiveCampaign(0, Set.of(DayOfWeek.WEDNESDAY));
        givenDueTargets(5);
        when(state.countClusterCapacity(anyInt())).thenReturn(3);
        when(state.countActiveTotal()).thenReturn(2);

        dialerAt(10).dispatch();

        verify(rabbit, times(1)).convertAndSend(eq(CallTask.QUEUE), any(CallTask.class));
    }

    @Test
    void drainingStopsDispatchEntirely() {
        givenActiveCampaign(0, Set.of(DayOfWeek.WEDNESDAY));
        givenDueTargets(2);
        GracefulShutdownManager shutdown = mock(GracefulShutdownManager.class);
        when(shutdown.isDraining()).thenReturn(true);
        DialerService dialer = new DialerService(
                new DialerProperties(true, 5, 10, 5, 5, 60, 0),
                RTP_PROPS, campaignUseCase, campaignTargetUseCase, mock(CompanyConfigUseCase.class),
                sipTrunks, rabbit, state,
                mock(OutboundCallRegistry.class), mock(AriService.class), shutdown, ttsWarmup,
                Clock.systemDefaultZone(), mock(CampaignVariantUseCase.class), aiAgents, callBilling,
                mock(CallAttemptUseCase.class), mock(TrunkHealth.class), (jobName, job) -> {
                    job.run();
                    return true;
                }, companyConcurrency());

        dialer.dispatch();

        verify(campaignUseCase, never()).findActiveCampaigns();
    }
}
