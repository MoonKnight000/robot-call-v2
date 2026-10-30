package uz.murodjon.robotcallv2.dialer.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.agent.lifecycle.GracefulShutdownManager;
import uz.murodjon.robotcallv2.agent.queue.ProgressiveDialer;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.callqueue.application.port.input.CallQueueUseCase;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueue;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueueMember;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignSchedule;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignRetry;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignLimits;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignRecurrence;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignDialing;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.enums.*;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.dialer.infrastructure.config.DialerProperties;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorAccountUseCase;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorPresenceUseCase;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorPresence;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorSipAccount;
import uz.murodjon.robotcallv2.operator.domain.enums.OperatorStatus;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProgressiveDialServiceTest {

    private static final long COMPANY_ID = 1L;
    private static final long CAMPAIGN_ID = 3L;
    private static final long QUEUE_ID = 4L;
    private static final long FIRST_OPERATOR = 20L;
    private static final long SECOND_OPERATOR = 21L;
    /** 10:00 UTC — inside the campaign's 09:00–20:00 window; the company has no settings. */
    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");

    private CampaignUseCase campaigns;
    private CampaignTargetUseCase targets;
    private CallQueueUseCase queues;
    private CallBillingUseCase billing;
    private OperatorPresenceUseCase presence;
    private OperatorAccountUseCase accounts;
    private AriService ari;
    private ProgressiveDialer dialer;
    private ProgressiveDialService service;

    @BeforeEach
    void setUp() {
        campaigns = mock(CampaignUseCase.class);
        targets = mock(CampaignTargetUseCase.class);
        queues = mock(CallQueueUseCase.class);
        billing = mock(CallBillingUseCase.class);
        presence = mock(OperatorPresenceUseCase.class);
        accounts = mock(OperatorAccountUseCase.class);
        ari = mock(AriService.class);
        dialer = mock(ProgressiveDialer.class);
        DialerProperties dialerProperties = mock(DialerProperties.class);
        when(dialerProperties.enabled()).thenReturn(true);
        CompanyConfigUseCase companyConfig = mock(CompanyConfigUseCase.class);
        when(campaigns.findActiveCampaigns()).thenReturn(List.of(campaign(DialMode.PROGRESSIVE)));
        when(queues.findQueue(COMPANY_ID, QUEUE_ID)).thenReturn(queue(FIRST_OPERATOR, SECOND_OPERATOR));
        when(billing.hasBalanceForCall(COMPANY_ID)).thenReturn(true);
        when(presence.claimForRinging(anyLong(), anyString())).thenReturn(true);
        when(ari.isEndpointOnline(anyString())).thenReturn(true);
        operatorIs(FIRST_OPERATOR, OperatorStatus.AVAILABLE);
        operatorIs(SECOND_OPERATOR, OperatorStatus.AVAILABLE);
        service = new ProgressiveDialService(dialerProperties, campaigns, targets, queues, companyConfig,
                mock(CallAttemptUseCase.class), billing, presence, accounts, ari, dialer,
                mock(GracefulShutdownManager.class), mock(ClusterJobLock.class), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void dialsOneClientForEachFreeOperator() {
        when(targets.claimNextForOperator(COMPANY_ID, CAMPAIGN_ID, FIRST_OPERATOR)).thenReturn(target(300L));
        when(targets.claimNextForOperator(COMPANY_ID, CAMPAIGN_ID, SECOND_OPERATOR)).thenReturn(target(301L));

        service.dialForFreeOperators();

        verify(dialer).dial(COMPANY_ID, FIRST_OPERATOR, 300L, "998901112233", "uz-UZ");
        verify(dialer).dial(COMPANY_ID, SECOND_OPERATOR, 301L, "998901112233", "uz-UZ");
    }

    @Test
    void operatorOnACallGetsNoClient() {
        operatorIs(SECOND_OPERATOR, OperatorStatus.ON_CALL);
        when(targets.claimNextForOperator(COMPANY_ID, CAMPAIGN_ID, FIRST_OPERATOR)).thenReturn(target(300L));

        service.dialForFreeOperators();

        verify(presence, never()).claimForRinging(eq(SECOND_OPERATOR), anyString());
        verify(dialer, times(1)).dial(anyLong(), anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void operatorWithoutASignedInSoftphoneGetsNoClient() {
        when(ari.isEndpointOnline("op-21")).thenReturn(false);
        when(targets.claimNextForOperator(COMPANY_ID, CAMPAIGN_ID, FIRST_OPERATOR)).thenReturn(target(300L));

        service.dialForFreeOperators();

        verify(presence, never()).claimForRinging(eq(SECOND_OPERATOR), anyString());
        verify(targets, never()).claimNextForOperator(COMPANY_ID, CAMPAIGN_ID, SECOND_OPERATOR);
    }

    @Test
    void operatorIsGivenBackWhenNothingIsDue() {
        service.dialForFreeOperators();

        verify(presence).releaseRinging(FIRST_OPERATOR);
        verify(presence, never()).claimForRinging(eq(SECOND_OPERATOR), anyString());
        verifyNoInteractions(dialer);
    }

    @Test
    void failedDialGivesTheOperatorBackAndRetriesTheTarget() {
        when(targets.claimNextForOperator(COMPANY_ID, CAMPAIGN_ID, FIRST_OPERATOR)).thenReturn(target(300L));
        when(dialer.dial(COMPANY_ID, FIRST_OPERATOR, 300L, "998901112233", "uz-UZ"))
                .thenThrow(new ExternalServiceException(ErrorCode.ARI_ORIGINATE_FAILED, "asterisk", null, "x", "y",
                        "down"));

        service.dialForFreeOperators();

        verify(presence).releaseRinging(FIRST_OPERATOR);
        verify(targets).applyOutcome(COMPANY_ID, 300L, Disposition.FAILED);
    }

    @Test
    void campaignWithoutBalanceIsNotDialled() {
        when(billing.hasBalanceForCall(COMPANY_ID)).thenReturn(false);

        service.dialForFreeOperators();

        verifyNoInteractions(presence, dialer);
    }

    @Test
    void otherDialModesAreLeftToTheirOwnDialers() {
        when(campaigns.findActiveCampaigns()).thenReturn(List.of(campaign(DialMode.PREVIEW), campaign(DialMode.AI)));

        service.dialForFreeOperators();

        verifyNoInteractions(queues, presence, dialer);
    }

    private void operatorIs(long userId, OperatorStatus status) {
        when(presence.find(COMPANY_ID, userId)).thenReturn(
                OperatorPresence.offline(userId, COMPANY_ID, NOW).withStatus(status, null, NOW, null));
        when(accounts.findSipAccount(COMPANY_ID, userId)).thenReturn(
                new OperatorSipAccount(userId, COMPANY_ID, "op-" + userId, "secret", false, null, NOW, NOW));
    }

    private static CampaignTarget target(long id) {
        return new CampaignTarget(id, CAMPAIGN_ID, 1L, "998901112233", null, null, TargetStatus.IN_PROGRESS, 1,
                false, NOW.plus(CampaignTarget.OPERATOR_CLAIM_LEASE));
    }

    private static Campaign campaign(DialMode dialMode) {
        return Campaign.builder()
                .id(CAMPAIGN_ID)
                .name("Progressive")
                .type(CampaignType.DEBT_COLLECTION)
                .status(CampaignStatus.ACTIVE)
                .schedule(new CampaignSchedule(null, null, LocalTime.of(9, 0), LocalTime.of(20, 0), Set.of()))
                .retry(new CampaignRetry(3, 24, null))
                .limits(new CampaignLimits(5, 0, null, null))
                .companyId(COMPANY_ID)
                .recurrence(new CampaignRecurrence(RecurrenceType.ONCE, null, null, false))
                .dialing(new CampaignDialing(dialMode, QUEUE_ID, Campaign.DEFAULT_MAX_AI_OVERFLOW_PERCENT, null))
                .build();
    }

    private static CallQueue queue(Long... memberUserIds) {
        return new CallQueue(QUEUE_ID, COMPANY_ID, "Sotuv", null, 20, 0, 0, 0, 0, false, null, 0, null, 0, null,
                null, null, 20, 0, false, null, true, true,
                Arrays.stream(memberUserIds).map(userId -> new CallQueueMember(userId, 0, Set.of())).toList(), NOW);
    }
}
