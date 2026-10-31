package uz.murodjon.robotcallv2.dialer.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.agent.queue.OperatorFirstDialer;
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
import uz.murodjon.robotcallv2.dialer.application.dto.PreviewCardResponse;
import uz.murodjon.robotcallv2.memory.application.port.input.ClientMemoryUseCase;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.exception.AppException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PreviewDialServiceTest {

    private static final long COMPANY_ID = 1L;
    private static final long CAMPAIGN_ID = 3L;
    private static final long QUEUE_ID = 4L;
    private static final long OPERATOR_ID = 20L;
    private static final long TARGET_ID = 300L;
    /** 10:00 UTC — inside the campaign's 09:00–20:00 window; the company has no settings. */
    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");

    private CampaignUseCase campaigns;
    private CampaignTargetUseCase targets;
    private CallQueueUseCase queues;
    private CallAttemptUseCase calls;
    private CallBillingUseCase billing;
    private OperatorFirstDialer dialer;
    private PreviewDialService service;

    @BeforeEach
    void setUp() {
        campaigns = mock(CampaignUseCase.class);
        targets = mock(CampaignTargetUseCase.class);
        queues = mock(CallQueueUseCase.class);
        calls = mock(CallAttemptUseCase.class);
        billing = mock(CallBillingUseCase.class);
        dialer = mock(OperatorFirstDialer.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        when(currentUser.id()).thenReturn(Optional.of(OPERATOR_ID));
        when(campaigns.requireCampaign(COMPANY_ID, CAMPAIGN_ID)).thenReturn(campaign(CampaignStatus.ACTIVE));
        when(queues.findQueue(COMPANY_ID, QUEUE_ID)).thenReturn(queue(OPERATOR_ID));
        when(billing.hasBalanceForCall(COMPANY_ID)).thenReturn(true);
        service = new PreviewDialService(campaigns, targets, queues, mock(CompanyConfigUseCase.class), calls, billing,
                mock(ClientMemoryUseCase.class), dialer, currentUser, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void heldCardComesBackWithoutClaimingAnother() {
        when(targets.findHeldByOperator(COMPANY_ID, CAMPAIGN_ID, OPERATOR_ID)).thenReturn(target());

        PreviewCardResponse card = service.findNextCard(COMPANY_ID, CAMPAIGN_ID);

        assertThat(card.target().id()).isEqualTo(TARGET_ID);
        verify(targets, never()).claimNextForOperator(anyLong(), anyLong(), anyLong());
    }

    @Test
    void newCardIsClaimedForTheOperator() {
        when(targets.claimNextForOperator(COMPANY_ID, CAMPAIGN_ID, OPERATOR_ID)).thenReturn(target());

        PreviewCardResponse card = service.findNextCard(COMPANY_ID, CAMPAIGN_ID);

        assertThat(card.target().id()).isEqualTo(TARGET_ID);
        assertThat(card.callId()).isNull();
    }

    @Test
    void operatorOutsideTheQueueIsForbidden() {
        when(queues.findQueue(COMPANY_ID, QUEUE_ID)).thenReturn(queue(99L));

        assertThatThrownBy(() -> service.findNextCard(COMPANY_ID, CAMPAIGN_ID))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(targets);
    }

    @Test
    void pausedCampaignHandsOutNoNewCard() {
        when(campaigns.requireCampaign(COMPANY_ID, CAMPAIGN_ID)).thenReturn(campaign(CampaignStatus.PAUSED));

        assertThatThrownBy(() -> service.findNextCard(COMPANY_ID, CAMPAIGN_ID))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.CAMPAIGN_NOT_DIALLING));
        verify(targets, never()).claimNextForOperator(anyLong(), anyLong(), anyLong());
    }

    @Test
    void dialRingsTheOperatorForTheHeldCard() {
        when(targets.findHeldByOperator(COMPANY_ID, CAMPAIGN_ID, OPERATOR_ID)).thenReturn(target());

        service.callTarget(COMPANY_ID, CAMPAIGN_ID, TARGET_ID);

        verify(dialer).dialTarget(COMPANY_ID, OPERATOR_ID, "998901112233", TARGET_ID);
    }

    @Test
    void cardNotHeldCannotBeDialled() {
        assertThatThrownBy(() -> service.callTarget(COMPANY_ID, CAMPAIGN_ID, TARGET_ID))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.PREVIEW_TARGET_NOT_HELD));
        verifyNoInteractions(dialer);
    }

    @Test
    void skipGivesTheCardBackForFifteenMinutes() {
        when(targets.findHeldByOperator(COMPANY_ID, CAMPAIGN_ID, OPERATOR_ID)).thenReturn(target());

        service.releaseTarget(COMPANY_ID, CAMPAIGN_ID, TARGET_ID);

        verify(targets).releaseClaim(TARGET_ID, NOW.plusSeconds(15 * 60));
    }

    @Test
    void dialledCardMustBeWrappedUpNotSkipped() {
        when(targets.findHeldByOperator(COMPANY_ID, CAMPAIGN_ID, OPERATOR_ID)).thenReturn(target());
        when(calls.findLatestAttemptIdByTargetIdSince(eq(COMPANY_ID), eq(TARGET_ID), any())).thenReturn(42L);

        assertThatThrownBy(() -> service.releaseTarget(COMPANY_ID, CAMPAIGN_ID, TARGET_ID))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.PREVIEW_WRAP_UP_REQUIRED));
        verify(targets, never()).releaseClaim(anyLong(), any());
    }

    private static CampaignTarget target() {
        return new CampaignTarget(TARGET_ID, CAMPAIGN_ID, 1L, "998901112233", "uz", null, TargetStatus.IN_PROGRESS,
                1, false, NOW.plus(CampaignTarget.OPERATOR_CLAIM_LEASE));
    }

    private static Campaign campaign(CampaignStatus status) {
        return Campaign.builder()
                .id(CAMPAIGN_ID)
                .name("Preview")
                .status(status)
                .schedule(new CampaignSchedule(null, null, LocalTime.of(9, 0), LocalTime.of(20, 0), Set.of()))
                .retry(new CampaignRetry(3, 24, null))
                .limits(new CampaignLimits(5, 0, null, null, null))
                .companyId(COMPANY_ID)
                .recurrence(new CampaignRecurrence(RecurrenceType.ONCE, null, null, false))
                .dialing(new CampaignDialing(DialMode.PREVIEW, QUEUE_ID, Campaign.DEFAULT_MAX_AI_OVERFLOW_PERCENT, null))
                .build();
    }

    private static CallQueue queue(long memberUserId) {
        return new CallQueue(QUEUE_ID, COMPANY_ID, "Sotuv", null, 20, 0, 0, 0, 0, false, null, 0, null, 0, null,
                null, null, 20, 0, false, null, true, true, List.of(new CallQueueMember(memberUserId, 0, Set.of())),
                NOW);
    }
}
