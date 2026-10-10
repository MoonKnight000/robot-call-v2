package uz.murodjon.robotcallv2.dialer.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.agent.lifecycle.GracefulShutdownManager;
import uz.murodjon.robotcallv2.agent.queue.PredictiveDialer;
import uz.murodjon.robotcallv2.agent.rtp.RtpProperties;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.callqueue.application.port.input.CallQueueUseCase;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueue;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueueMember;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignType;
import uz.murodjon.robotcallv2.campaign.domain.enums.DialMode;
import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.dialer.domain.entity.DialPacingStats;
import uz.murodjon.robotcallv2.dialer.infrastructure.config.DialerProperties;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.donotcall.application.port.input.DoNotCallUseCase;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorAccountUseCase;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorPresenceUseCase;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorPresence;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorSipAccount;
import uz.murodjon.robotcallv2.operator.domain.enums.OperatorStatus;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PredictiveDialServiceTest {

    private static final long COMPANY_ID = 1L;
    private static final long CAMPAIGN_ID = 3L;
    private static final long QUEUE_ID = 4L;
    private static final long AGENT_ID = 7L;
    private static final long FIRST_OPERATOR = 20L;
    private static final long SECOND_OPERATOR = 21L;
    private static final String PHONE = "998901112233";
    /** 10:00 UTC — inside the campaign's 09:00–20:00 window; the company has no settings. */
    private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

    private CampaignTargetUseCase targets;
    private OperatorPresenceUseCase presence;
    private AiAgent agent;
    private DoNotCallUseCase doNotCall;
    private OutboundCallPreparer preparer;
    private DialerState state;
    private PredictiveDialer dialer;
    private PredictiveDialService service;

    @BeforeEach
    void setUp() {
        CampaignUseCase campaigns = mock(CampaignUseCase.class);
        targets = mock(CampaignTargetUseCase.class);
        CallQueueUseCase queues = mock(CallQueueUseCase.class);
        CallBillingUseCase billing = mock(CallBillingUseCase.class);
        presence = mock(OperatorPresenceUseCase.class);
        OperatorAccountUseCase accounts = mock(OperatorAccountUseCase.class);
        AiAgentUseCase agents = mock(AiAgentUseCase.class);
        doNotCall = mock(DoNotCallUseCase.class);
        preparer = mock(OutboundCallPreparer.class);
        state = mock(DialerState.class);
        AriService ari = mock(AriService.class);
        dialer = mock(PredictiveDialer.class);
        DialerProperties dialerProperties = mock(DialerProperties.class);
        when(dialerProperties.enabled()).thenReturn(true);
        when(dialerProperties.maxConcurrentCalls()).thenReturn(100);
        when(dialerProperties.maxConcurrentCallsPerCompany()).thenReturn(100);
        RtpProperties rtpProperties = mock(RtpProperties.class);
        when(rtpProperties.mediaCapacity()).thenReturn(100);
        when(state.countClusterCapacity(100)).thenReturn(100);

        when(campaigns.findActiveCampaigns()).thenReturn(List.of(campaign()));
        CallQueue queue = mock(CallQueue.class);
        when(queue.members()).thenReturn(List.of(new CallQueueMember(FIRST_OPERATOR, 0, Set.of()),
                new CallQueueMember(SECOND_OPERATOR, 0, Set.of())));
        when(queues.findQueue(COMPANY_ID, QUEUE_ID)).thenReturn(queue);
        when(billing.hasBalanceForCall(COMPANY_ID)).thenReturn(true);
        agent = mock(AiAgent.class);
        when(agent.enabled()).thenReturn(true);
        when(agent.id()).thenReturn(AGENT_ID);
        when(agent.language()).thenReturn("uz-UZ");
        when(agents.requirePublishedAgent(COMPANY_ID, AGENT_ID)).thenReturn(agent);
        when(accounts.findSipAccount(eq(COMPANY_ID), anyLong())).thenReturn(new OperatorSipAccount(FIRST_OPERATOR,
                COMPANY_ID, "op", "secret", false, null, NOW, NOW));
        when(ari.isEndpointOnline("op")).thenReturn(true);
        operatorIs(FIRST_OPERATOR, OperatorStatus.AVAILABLE);
        operatorIs(SECOND_OPERATOR, OperatorStatus.AVAILABLE);
        when(dialer.findStats(CAMPAIGN_ID)).thenReturn(DialPacingStats.EMPTY);
        when(preparer.prepare(any())).thenReturn(mock(OutboundCall.class));

        service = new PredictiveDialService(dialerProperties, rtpProperties, campaigns, targets, queues,
                mock(CompanyConfigUseCase.class), mock(CallAttemptUseCase.class), billing, agents, doNotCall,
                presence, accounts, preparer, state, ari, dialer,
                mock(GracefulShutdownManager.class), mock(ClusterJobLock.class), Clock.fixed(NOW, ZoneOffset.UTC),
                new CompanyConcurrencyService(dialerProperties, state, mock(CompanyUseCase.class),
                        mock(NotificationUseCase.class), Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    @Test
    void withoutHistoryOneClientIsDialledPerFreeOperator() {
        service.dialAhead();

        verify(targets).claimDueTargets(CAMPAIGN_ID, 2);
    }

    @Test
    void lowAnswerRateDialsAheadOfTheOperators() {
        when(dialer.findStats(CAMPAIGN_ID)).thenReturn(new DialPacingStats(40, 20, 0));
        when(dialer.countUndecided(CAMPAIGN_ID)).thenReturn(1);

        service.dialAhead();

        verify(targets).claimDueTargets(CAMPAIGN_ID, 3); // 2 operators × 2, less the one still ringing
    }

    @Test
    void noFreeOperatorClaimsNothing() {
        operatorIs(FIRST_OPERATOR, OperatorStatus.ON_CALL);
        operatorIs(SECOND_OPERATOR, OperatorStatus.BREAK);

        service.dialAhead();

        verify(targets, never()).claimDueTargets(anyLong(), anyInt());
    }

    @Test
    void disabledAgentDialsNothing() {
        when(agent.enabled()).thenReturn(false);

        service.dialAhead();

        verify(targets, never()).claimDueTargets(anyLong(), anyInt());
    }

    @Test
    void claimedTargetIsDialledOnAReservedSlot() {
        when(targets.claimDueTargets(CAMPAIGN_ID, 2)).thenReturn(List.of(target(300L)));

        service.dialAhead();

        verify(state).reserve(COMPANY_ID, CAMPAIGN_ID);
        verify(dialer).dial(eq(QUEUE_ID), any());
    }

    @Test
    void numberOnTheDoNotCallListIsNotDialled() {
        when(targets.claimDueTargets(CAMPAIGN_ID, 2)).thenReturn(List.of(target(300L)));
        when(doNotCall.contains(COMPANY_ID, PHONE)).thenReturn(true);

        service.dialAhead();

        verify(targets).applyOutcome(COMPANY_ID, 300L, Disposition.DO_NOT_CALL);
        verify(state, never()).reserve(anyLong(), anyLong());
        verify(dialer, never()).dial(anyLong(), any());
    }

    @Test
    void failedDialGivesTheSlotBack() {
        when(targets.claimDueTargets(CAMPAIGN_ID, 2)).thenReturn(List.of(target(300L)));
        when(dialer.dial(eq(QUEUE_ID), any())).thenThrow(new RuntimeException("ARI down"));

        service.dialAhead();

        verify(state).release(COMPANY_ID, CAMPAIGN_ID);
        verify(targets).applyOutcome(COMPANY_ID, 300L, Disposition.FAILED);
    }

    private void operatorIs(long userId, OperatorStatus status) {
        OperatorPresence operatorPresence = mock(OperatorPresence.class);
        when(operatorPresence.status()).thenReturn(status);
        when(presence.find(COMPANY_ID, userId)).thenReturn(operatorPresence);
    }

    private static CampaignTarget target(long id) {
        return new CampaignTarget(id, CAMPAIGN_ID, 1L, PHONE, null, null, TargetStatus.IN_PROGRESS, 1, false,
                NOW.plusSeconds(300));
    }

    private static Campaign campaign() {
        return Campaign.builder()
                .id(CAMPAIGN_ID)
                .name("Predictive")
                .type(CampaignType.DEBT_COLLECTION)
                .status(CampaignStatus.ACTIVE)
                .dialWindowStart(LocalTime.of(9, 0))
                .dialWindowEnd(LocalTime.of(20, 0))
                .dialDays(Set.of())
                .maxAttempts(3)
                .retryIntervalMinutes(24)
                .aiAgentId(AGENT_ID)
                .companyId(COMPANY_ID)
                .recurrenceType(RecurrenceType.ONCE)
                .dialMode(DialMode.PREDICTIVE)
                .callQueueId(QUEUE_ID)
                .build();
    }
}
