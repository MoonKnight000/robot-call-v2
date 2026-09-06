package uz.murodjon.robotcallv2.dialer.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.agent.lifecycle.GracefulShutdownManager;
import uz.murodjon.robotcallv2.agent.rtp.RtpProperties;
import uz.murodjon.robotcallv2.agent.rtp.WavRecorder;
import uz.murodjon.robotcallv2.agent.tts.TtsWarmup;
import uz.murodjon.robotcallv2.aiagent.AiAgentFixtures;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignType;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignVariantUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignTargetRepository;
import uz.murodjon.robotcallv2.campaign.application.service.CampaignService;
import uz.murodjon.robotcallv2.company.application.service.CompanyConfigService;
import uz.murodjon.robotcallv2.dialer.infrastructure.config.DialerProperties;
import uz.murodjon.robotcallv2.dialer.infrastructure.config.RabbitConfig;
import uz.murodjon.robotcallv2.dialer.infrastructure.config.RetryProperties;
import uz.murodjon.robotcallv2.dialer.application.dto.CallTask;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.SipTrunkUseCase;

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

class DialerServiceTest {

    private static final long CAMPAIGN_ID = 3L;
    private static final long AI_AGENT_ID = 7L;
    private static final ZoneId ZONE = ZoneId.systemDefault();

    /** 51 even ports — well clear of the limits under test, so only those are exercised. */
    private static final RtpProperties RTP_PROPS =
            new RtpProperties("127.0.0.1", 10000, 10100, "recordings", "", 2, WavRecorder.RecordingMode.SPATIAL_STEREO);

    private CampaignRepository campaigns;
    private CampaignTargetRepository targets;
    private SipTrunkUseCase sipTrunks;
    private RabbitTemplate rabbit;
    private DialerState state;
    private TtsWarmup ttsWarmup;
    private AiAgentUseCase aiAgents;

    @BeforeEach
    void setUp() {
        campaigns = mock(CampaignRepository.class);
        targets = mock(CampaignTargetRepository.class);
        sipTrunks = mock(SipTrunkUseCase.class);
        rabbit = mock(RabbitTemplate.class);
        state = mock(DialerState.class);
        ttsWarmup = mock(TtsWarmup.class);
        aiAgents = mock(AiAgentUseCase.class);
        when(aiAgents.requireAgent(1L, AI_AGENT_ID))
                .thenReturn(AiAgentFixtures.agent(AI_AGENT_ID, 1L, 10L, "uz-UZ", "dilfuza"));
        when(state.active(anyLong())).thenReturn(0);
        when(state.activeTotal()).thenReturn(0);
        when(sipTrunks.findTrunksForCall(anyLong(), any())).thenReturn(List.of());
    }

    private DialerService dialerAt(int hour) {
        ZonedDateTime now = ZonedDateTime.of(LocalDate.of(2026, 7, 1), LocalTime.of(hour, 0), ZONE);
        return new DialerService(
                new DialerProperties(true, 5, 10, 5, 5, 60,
                        new RetryProperties(180, 15, 1200, true)),
                RTP_PROPS, campaigns, targets, mock(CampaignService.class), mock(CompanyConfigService.class),
                sipTrunks, rabbit, state,
                mock(OutboundCallRegistry.class), mock(AriService.class),
                mock(GracefulShutdownManager.class), ttsWarmup,
                Clock.fixed(now.toInstant(), ZONE), mock(CampaignVariantUseCase.class), aiAgents);
    }

    private void givenActiveCampaign(int dailyCallCap, Set<DayOfWeek> dialDays) {
        when(campaigns.findActive()).thenReturn(List.of(new Campaign(
                CAMPAIGN_ID, "c", CampaignType.DEBT_COLLECTION, CampaignStatus.ACTIVE,
                LocalTime.of(9, 0), LocalTime.of(20, 0), dialDays, 3, 24, 5, dailyCallCap, AI_AGENT_ID, 1L, null,
                RecurrenceType.ONCE, null, null, false, null)));
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

        verify(targets).claimDue(CAMPAIGN_ID, 2);
        verify(rabbit, times(2)).convertAndSend(eq(RabbitConfig.CALL_TASK_QUEUE), any(CallTask.class));
    }

    @Test
    void anExhaustedDailyCapStopsTheCampaignWithoutClaimingTargets() {
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

        verify(targets).claimDue(CAMPAIGN_ID, 5);
    }

    @Test
    void everyDispatchIsCountedAgainstTheDailyCap() {
        givenActiveCampaign(5, Set.of(DayOfWeek.WEDNESDAY));
        givenDueTargets(2);

        dialerAt(10).dispatch();

        verify(state, times(2)).countDispatch(eq(CAMPAIGN_ID), any(LocalDate.class));
        verify(state, times(2)).reserve(anyLong());
    }

    @Test
    void drainingStopsDispatchEntirely() {
        givenActiveCampaign(0, Set.of(DayOfWeek.WEDNESDAY));
        givenDueTargets(2);
        GracefulShutdownManager shutdown = mock(GracefulShutdownManager.class);
        when(shutdown.isDraining()).thenReturn(true);
        DialerService dialer = new DialerService(
                new DialerProperties(true, 5, 10, 5, 5, 60,
                        new RetryProperties(180, 15, 1200, true)),
                RTP_PROPS, campaigns, targets, mock(CampaignService.class), mock(CompanyConfigService.class),
                sipTrunks, rabbit, state,
                mock(OutboundCallRegistry.class), mock(AriService.class), shutdown, ttsWarmup,
                Clock.systemDefaultZone(), mock(CampaignVariantUseCase.class), aiAgents);

        dialer.dispatch();

        verify(campaigns, never()).findActive();
    }
}
