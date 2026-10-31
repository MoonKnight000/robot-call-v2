package uz.murodjon.robotcallv2.dialer.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignSchedule;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignRetry;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignLimits;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignRecurrence;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignDialing;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.enums.*;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.dialer.application.dto.CallTask;
import uz.murodjon.robotcallv2.dialer.infrastructure.config.DialerProperties;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.phonenumber.application.port.input.PhoneNumberUseCase;
import uz.murodjon.robotcallv2.phonenumber.domain.entity.PhoneNumber;
import uz.murodjon.robotcallv2.phonenumber.domain.enums.PhoneNumberStatus;
import uz.murodjon.robotcallv2.phonenumber.domain.enums.PhoneNumberType;
import uz.murodjon.robotcallv2.schedule.application.port.input.BusinessScheduleUseCase;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignNumbers;
import uz.murodjon.robotcallv2.siptrunk.application.dto.SipTrunkRow;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.CallerIdPoolUseCase;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.SipTrunkUseCase;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.CallerIdPoolNumber;
import uz.murodjon.robotcallv2.siptrunk.domain.enums.CallerIdNumberStatus;
import uz.murodjon.robotcallv2.siptrunk.domain.enums.SipTrunkTransport;

import java.time.*;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
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
    private CallerIdPoolUseCase callerIdPools;
    private PhoneNumberUseCase phoneNumbers;

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
        callerIdPools = mock(CallerIdPoolUseCase.class);
        phoneNumbers = mock(PhoneNumberUseCase.class);

        // A mock says false to boolean methods by default: every call is refused unless this
        // says otherwise.
        when(callBilling.hasBalanceForCall(anyLong())).thenReturn(true);
        when(callBilling.reserveForCall(anyLong(), anyLong())).thenReturn(true);
        when(aiAgents.requirePublishedAgent(1L, AI_AGENT_ID))
                .thenReturn(AiAgentFixtures.agent(AI_AGENT_ID, 1L, 10L, "uz-UZ", "dilfuza"));
        when(state.countActive(anyLong())).thenReturn(0);
        when(state.countActiveTotal()).thenReturn(0);
        when(state.countClusterCapacity(anyInt())).thenAnswer(call -> call.getArgument(0));
        when(state.countDispatchedToday(anyLong(), any())).thenReturn(OptionalInt.of(0));
        when(sipTrunks.findAllEnabledForCompany(anyLong())).thenReturn(List.of());
    }

    private DialerService dialerAt(int hour) {
        ZonedDateTime now = ZonedDateTime.of(LocalDate.of(2026, 7, 1), LocalTime.of(hour, 0), ZONE);
        return new DialerService(
                new DialerProperties(true, 5, 10, 5, 5, 60, 0),
                RTP_PROPS, campaignUseCase, campaignTargetUseCase, mock(CompanyConfigUseCase.class),
                mock(BusinessScheduleUseCase.class),
                sipTrunks, rabbit, state,
                mock(OutboundCallRegistry.class), mock(AriService.class),
                mock(GracefulShutdownManager.class), ttsWarmup,
                Clock.fixed(now.toInstant(), ZONE), mock(CampaignVariantUseCase.class), aiAgents, callBilling,
                mock(CallAttemptUseCase.class), mock(TrunkHealth.class), (jobName, job) -> {
                    job.run();
                    return true;
                }, companyConcurrency(), callerIdPools, phoneNumbers);
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
                .status(CampaignStatus.ACTIVE)
                .schedule(new CampaignSchedule(null, null, LocalTime.of(9, 0), LocalTime.of(20, 0), dialDays))
                .retry(new CampaignRetry(3, 24, null))
                .limits(new CampaignLimits(5, dailyCallCap, null, null, null))
                .aiAgentId(AI_AGENT_ID)
                .companyId(1L)
                .recurrence(new CampaignRecurrence(RecurrenceType.ONCE, null, null, false))
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
                .status(CampaignStatus.ACTIVE)
                .schedule(new CampaignSchedule(null, null, LocalTime.of(9, 0), LocalTime.of(20, 0),
                        Set.of(DayOfWeek.WEDNESDAY)))
                .retry(new CampaignRetry(3, 24, null))
                .limits(new CampaignLimits(5, 0, null, null, null))
                .aiAgentId(AI_AGENT_ID)
                .companyId(1L)
                .recurrence(new CampaignRecurrence(RecurrenceType.ONCE, null, null, false))
                .dialing(new CampaignDialing(null, null, Campaign.DEFAULT_MAX_AI_OVERFLOW_PERCENT, TargetOrdering.SCORE))
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
                mock(BusinessScheduleUseCase.class),
                sipTrunks, rabbit, state,
                mock(OutboundCallRegistry.class), mock(AriService.class), shutdown, ttsWarmup,
                Clock.systemDefaultZone(), mock(CampaignVariantUseCase.class), aiAgents, callBilling,
                mock(CallAttemptUseCase.class), mock(TrunkHealth.class), (jobName, job) -> {
                    job.run();
                    return true;
                }, companyConcurrency(), callerIdPools, phoneNumbers);

        dialer.dispatch();

        verify(campaignUseCase, never()).findActiveCampaigns();
    }

    private static SipTrunkRow trunk(long id, Integer maxChannels) {
        return new SipTrunkRow(id, "t" + id, "trunk-" + id, null, false, null, 5060, null,
                SipTrunkTransport.UDP, List.of("alaw"), null, maxChannels, false, true, Instant.EPOCH);
    }

    /** A trunk already holding as many calls as its provider gives channels gets none of the batch. */
    @Test
    void aTrunkAtItsChannelLimitIsSkipped() {
        givenActiveCampaign(0, Set.of(DayOfWeek.WEDNESDAY));
        givenDueTargets(3);
        when(sipTrunks.findAllEnabledForCompany(anyLong())).thenReturn(List.of(trunk(1L, 2), trunk(2L, null)));
        when(state.countActiveForTrunk(1L)).thenReturn(2);

        dialerAt(10).dispatch();

        ArgumentCaptor<CallTask> tasks = ArgumentCaptor.forClass(CallTask.class);
        verify(rabbit, times(3)).convertAndSend(eq(CallTask.QUEUE), tasks.capture());
        assertThat(tasks.getAllValues()).extracting(CallTask::sipTrunkId).containsOnly(2L);
        verify(state, times(3)).reserveTrunk(2L);
    }

    @Test
    void freeChannelsBoundTheBatch() {
        givenActiveCampaign(0, Set.of(DayOfWeek.WEDNESDAY));
        givenDueTargets(10);
        when(sipTrunks.findAllEnabledForCompany(anyLong())).thenReturn(List.of(trunk(1L, 3)));
        when(state.countActiveForTrunk(1L)).thenReturn(1);

        dialerAt(10).dispatch();

        verify(campaignTargetUseCase).claimDueTargets(CAMPAIGN_ID, 2);
    }

    private static final long POOL_ID = 50L;

    private void givenActivePoolCampaign(boolean localPresence) {
        when(campaignUseCase.findActiveCampaigns()).thenReturn(List.of(Campaign.builder()
                .id(CAMPAIGN_ID)
                .name("c")
                .status(CampaignStatus.ACTIVE)
                .schedule(new CampaignSchedule(null, null, LocalTime.of(9, 0), LocalTime.of(20, 0),
                        Set.of(DayOfWeek.WEDNESDAY)))
                .retry(new CampaignRetry(3, 24, null))
                .limits(new CampaignLimits(5, 0, null, null, null))
                .aiAgentId(AI_AGENT_ID)
                .companyId(1L)
                .recurrence(new CampaignRecurrence(RecurrenceType.ONCE, null, null, false))
                .numbers(new CampaignNumbers(POOL_ID, localPresence, null))
                .build()));
    }

    private static CallerIdPoolNumber poolNumber(String phone, long trunkId) {
        return new CallerIdPoolNumber(1L, POOL_ID, phone, null, 0, 0, CallerIdNumberStatus.ACTIVE, null, null,
                Instant.EPOCH, trunkId);
    }

    /** A pool number goes out on its own trunk — any of the company's. */
    @Test
    void aPoolCampaignDialsEachNumberOverItsOwnTrunk() {
        givenActivePoolCampaign(false);
        givenDueTargets(1);
        when(sipTrunks.findAllEnabledForCompany(1L)).thenReturn(List.of(trunk(1L, null), trunk(2L, null)));
        when(callerIdPools.acquireCallerIdOnTrunks(eq(1L), eq(POOL_ID), isNull(), eq(Set.of(1L, 2L))))
                .thenReturn(poolNumber("+998712000201", 2L));

        dialerAt(10).dispatch();

        ArgumentCaptor<CallTask> task = ArgumentCaptor.forClass(CallTask.class);
        verify(rabbit).convertAndSend(eq(CallTask.QUEUE), task.capture());
        assertThat(task.getValue().sipTrunkId()).isEqualTo(2L);
        assertThat(task.getValue().callerId()).isEqualTo("+998712000201");
        assertThat(task.getValue().callerIdPoolId()).isEqualTo(POOL_ID);
        verify(state).reserveTrunk(2L);
    }

    /** Only trunks with a free channel are offered to the pool, and local presence passes the callee's number. */
    @Test
    void aFullTrunkIsNotOfferedToThePool() {
        givenActivePoolCampaign(true);
        givenDueTargets(1);
        when(sipTrunks.findAllEnabledForCompany(1L)).thenReturn(List.of(trunk(1L, 2), trunk(2L, null)));
        when(state.countActiveForTrunk(1L)).thenReturn(2);
        when(callerIdPools.acquireCallerIdOnTrunks(eq(1L), eq(POOL_ID), eq("998901112230"), eq(Set.of(2L))))
                .thenReturn(poolNumber("+998712000202", 2L));

        dialerAt(10).dispatch();

        verify(rabbit).convertAndSend(eq(CallTask.QUEUE), any(CallTask.class));
    }

    /** No usable pool number: the target waits — not dialled, not billed, under no other number. */
    @Test
    void aPoolWithNoUsableNumberHoldsTheTarget() {
        givenActivePoolCampaign(false);
        givenDueTargets(1);
        when(sipTrunks.findAllEnabledForCompany(1L)).thenReturn(List.of(trunk(1L, null)));
        when(callerIdPools.acquireCallerIdOnTrunks(anyLong(), anyLong(), any(), any())).thenReturn(null);

        dialerAt(10).dispatch();

        verify(campaignTargetUseCase).releaseClaim(eq(100L), any(Instant.class));
        verify(callBilling, never()).reserveForCall(anyLong(), anyLong());
        verify(state, never()).reserve(anyLong(), anyLong());
        verify(rabbit, never()).convertAndSend(any(String.class), any(CallTask.class));
    }

    private static final long FROM_NUMBER_ID = 60L;

    private void givenActiveFromNumberCampaign() {
        when(campaignUseCase.findActiveCampaigns()).thenReturn(List.of(Campaign.builder()
                .id(CAMPAIGN_ID)
                .name("c")
                .status(CampaignStatus.ACTIVE)
                .schedule(new CampaignSchedule(null, null, LocalTime.of(9, 0), LocalTime.of(20, 0),
                        Set.of(DayOfWeek.WEDNESDAY)))
                .retry(new CampaignRetry(3, 24, null))
                .limits(new CampaignLimits(5, 0, null, null, null))
                .aiAgentId(AI_AGENT_ID)
                .companyId(1L)
                .recurrence(new CampaignRecurrence(RecurrenceType.ONCE, null, null, false))
                .numbers(new CampaignNumbers(null, false, FROM_NUMBER_ID))
                .build()));
    }

    private static PhoneNumber byoNumber(String phone, Long trunkId) {
        return PhoneNumber.builder()
                .id(FROM_NUMBER_ID)
                .companyId(1L)
                .phoneNumber(phone)
                .type(PhoneNumberType.BYO_SIP_TRUNK)
                .status(PhoneNumberStatus.ASSIGNED)
                .providerTrunkId(trunkId)
                .build();
    }

    /** A fixed-number campaign's every call goes out on that number's trunk and presents that number. */
    @Test
    void aFromNumberCampaignDialsOverTheNumbersOwnTrunk() {
        givenActiveFromNumberCampaign();
        givenDueTargets(2);
        when(sipTrunks.findAllEnabledForCompany(1L)).thenReturn(List.of(trunk(1L, null), trunk(2L, null)));
        when(phoneNumbers.findCompanyNumber(1L, FROM_NUMBER_ID)).thenReturn(byoNumber("+998712000301", 2L));

        dialerAt(10).dispatch();

        ArgumentCaptor<CallTask> tasks = ArgumentCaptor.forClass(CallTask.class);
        verify(rabbit, times(2)).convertAndSend(eq(CallTask.QUEUE), tasks.capture());
        assertThat(tasks.getAllValues()).extracting(CallTask::sipTrunkId).containsOnly(2L);
        assertThat(tasks.getAllValues()).extracting(CallTask::callerId).containsOnly("+998712000301");
    }

    /** The number's trunk is the campaign's only one, so its free channels bound the batch. */
    @Test
    void theFromNumbersTrunkChannelsBoundTheBatch() {
        givenActiveFromNumberCampaign();
        givenDueTargets(10);
        when(sipTrunks.findAllEnabledForCompany(1L)).thenReturn(List.of(trunk(1L, null), trunk(2L, 3)));
        when(state.countActiveForTrunk(2L)).thenReturn(1);
        when(phoneNumbers.findCompanyNumber(1L, FROM_NUMBER_ID)).thenReturn(byoNumber("+998712000301", 2L));

        dialerAt(10).dispatch();

        verify(campaignTargetUseCase).claimDueTargets(CAMPAIGN_ID, 2);
    }

    /** A number with no trunk of the company's own pauses the campaign — no call under another number. */
    @Test
    void anUnusableFromNumberPausesTheCampaign() {
        givenActiveFromNumberCampaign();
        givenDueTargets(2);
        when(sipTrunks.findAllEnabledForCompany(1L)).thenReturn(List.of(trunk(1L, null)));
        when(phoneNumbers.findCompanyNumber(1L, FROM_NUMBER_ID)).thenReturn(byoNumber("+998712000301", null));

        dialerAt(10).dispatch();

        verify(campaignUseCase).pauseByPlatform(eq(1L), eq(CAMPAIGN_ID), anyString());
        verify(campaignTargetUseCase, never()).claimDueTargets(anyLong(), anyInt());
        verify(rabbit, never()).convertAndSend(any(String.class), any(CallTask.class));
    }

    @Test
    void everyTrunkFullClaimsNothing() {
        givenActiveCampaign(0, Set.of(DayOfWeek.WEDNESDAY));
        givenDueTargets(10);
        when(sipTrunks.findAllEnabledForCompany(anyLong())).thenReturn(List.of(trunk(1L, 3)));
        when(state.countActiveForTrunk(1L)).thenReturn(3);

        dialerAt(10).dispatch();

        verify(campaignTargetUseCase, never()).claimDueTargets(anyLong(), anyInt());
    }
}
