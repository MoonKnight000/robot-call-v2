package uz.murodjon.robotcallv2.agent.queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.callqueue.application.port.input.CallQueueUseCase;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueue;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueueMember;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.dialer.application.port.input.DialerSlotUseCase;
import uz.murodjon.robotcallv2.dialer.application.port.input.OutboundCallTrackingUseCase;
import uz.murodjon.robotcallv2.dialer.domain.entity.DialPacingStats;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorAccountUseCase;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorPresenceUseCase;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorPresence;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorSipAccount;
import uz.murodjon.robotcallv2.operator.domain.enums.OperatorStatus;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PredictiveDialerTest {

    private static final long COMPANY_ID = 1L;
    private static final long CAMPAIGN_ID = 10L;
    private static final long QUEUE_ID = 5L;
    private static final long OPERATOR_ID = 20L;
    private static final long TARGET_ID = 300L;
    private static final long ATTEMPT_ID = 900L;
    private static final String PHONE = "998901112233";
    private static final String CLIENT = "client-1";
    private static final String CLIENT_NAME = "PJSIP/trunk-1";
    private static final String OPERATOR = "operator-1";
    private static final OutboundCall AI_CALL = new OutboundCall(CAMPAIGN_ID, TARGET_ID, null, PHONE, "uz-UZ", null,
            null, null, COMPANY_ID, null, null, null, null);

    private AriService ari;
    private OperatorPresenceUseCase presence;
    private OperatorPresence operatorPresence;
    private CallAttemptUseCase calls;
    private CampaignTargetUseCase targets;
    private OutboundCallTrackingUseCase tracking;
    private DialerSlotUseCase slots;
    private PredictiveDialer dialer;
    private String token;
    private Runnable machineFound;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ari = mock(AriService.class);
        presence = mock(OperatorPresenceUseCase.class);
        calls = mock(CallAttemptUseCase.class);
        targets = mock(CampaignTargetUseCase.class);
        tracking = mock(OutboundCallTrackingUseCase.class);
        slots = mock(DialerSlotUseCase.class);
        ObjectProvider<AriService> ariProvider = mock(ObjectProvider.class);
        when(ariProvider.getObject()).thenReturn(ari);

        CallQueue queue = mock(CallQueue.class);
        when(queue.members()).thenReturn(List.of(new CallQueueMember(OPERATOR_ID, 1, Set.of())));
        CallQueueUseCase queues = mock(CallQueueUseCase.class);
        when(queues.findQueue(COMPANY_ID, QUEUE_ID)).thenReturn(queue);
        operatorPresence = mock(OperatorPresence.class);
        when(operatorPresence.status()).thenReturn(OperatorStatus.AVAILABLE);
        when(presence.find(COMPANY_ID, OPERATOR_ID)).thenReturn(operatorPresence);
        when(presence.claimForRinging(OPERATOR_ID, "predictive")).thenReturn(true);
        OperatorAccountUseCase accounts = mock(OperatorAccountUseCase.class);
        when(accounts.findSipAccount(COMPANY_ID, OPERATOR_ID)).thenReturn(new OperatorSipAccount(OPERATOR_ID,
                COMPANY_ID, "op-20", "secret", false, null, Instant.now(), Instant.now()));
        when(ari.isEndpointOnline("op-20")).thenReturn(true);

        ExecutorService executor = mock(ExecutorService.class);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(executor).execute(any());
        when(ari.originateClientCall(eq(COMPANY_ID), eq(PHONE), anyString(), anyInt(), eq("uz-UZ"), eq(TARGET_ID)))
                .thenReturn(CLIENT);
        when(ari.buildOperatorDialString("op-20")).thenReturn("PJSIP/op-20");
        when(ari.originateToStasis(eq("PJSIP/op-20"), anyString(), anyInt(), anyString())).thenReturn(OPERATOR);
        when(ari.watchForAnsweringMachine(eq(CLIENT), any())).thenAnswer(invocation -> {
            machineFound = invocation.getArgument(1);
            return true;
        });
        when(ari.bridgeWithRecording(CLIENT, CLIENT_NAME, COMPANY_ID, PHONE, OPERATOR, "uz-UZ")).thenReturn(ATTEMPT_ID);
        when(calls.findAttemptIdByChannel(CLIENT)).thenReturn(ATTEMPT_ID);
        dialer = new PredictiveDialer(ariProvider, queues, accounts, presence, calls, targets, tracking, slots,
                executor, Clock.fixed(Instant.parse("2026-10-08T10:00:00Z"), ZoneOffset.UTC));

        dialer.dial(QUEUE_ID, AI_CALL);
        ArgumentCaptor<String> appArgs = ArgumentCaptor.forClass(String.class);
        verify(ari).originateClientCall(eq(COMPANY_ID), eq(PHONE), appArgs.capture(), anyInt(), eq("uz-UZ"),
                eq(TARGET_ID));
        token = appArgs.getValue().substring("predictive-client,".length());
    }

    @Test
    void dialledClientIsUndecidedAndItsTargetKeptLive() {
        verify(tracking).trackOperatorTarget(TARGET_ID);
        assertThat(dialer.countUndecided(CAMPAIGN_ID)).isEqualTo(1);
    }

    @Test
    void clientWhoNeverAnswersIsSettledFromTheCauseAndFreesTheSlot() {
        dialer.onChannelGone(CLIENT, 19);

        verify(targets).applyOutcome(COMPANY_ID, TARGET_ID, Disposition.NO_ANSWER);
        verify(slots).release(COMPANY_ID, CAMPAIGN_ID);
        verify(tracking).untrackOperatorTarget(TARGET_ID);
        assertThat(dialer.findStats(CAMPAIGN_ID)).isEqualTo(new DialPacingStats(1, 0, 0));
    }

    @Test
    void answeredClientRingsAFreeOperator() {
        dialer.onClientAnswered(token, CLIENT, CLIENT_NAME);

        verify(ari).watchForAnsweringMachine(eq(CLIENT), any());
        verify(ari).originateToStasis(eq("PJSIP/op-20"), eq("predictive-operator," + token), eq(15), anyString());
        verify(ari, never()).startOutboundAiCall(anyString(), anyString(), any());
    }

    @Test
    void operatorWhoAnswersIsJoinedToTheClient() {
        dialer.onClientAnswered(token, CLIENT, CLIENT_NAME);

        dialer.onOperatorAnswered(token, OPERATOR);

        verify(presence).markOnCall(OPERATOR_ID, OPERATOR);
        verify(calls).markOperatorAnswered(eq(ATTEMPT_ID), eq(OPERATOR_ID), anyInt());
        assertThat(dialer.findStats(CAMPAIGN_ID)).isEqualTo(new DialPacingStats(1, 1, 0));

        dialer.onChannelGone(CLIENT, 16);
        verify(slots).release(COMPANY_ID, CAMPAIGN_ID);
        verify(targets, never()).applyOutcome(anyLong(), anyLong(), any()); // the wrap-up settles it
    }

    @Test
    void noFreeOperatorHandsTheClientToTheAi() {
        when(operatorPresence.status()).thenReturn(OperatorStatus.ON_CALL);

        dialer.onClientAnswered(token, CLIENT, CLIENT_NAME);

        verify(ari).stopWatchingForAnsweringMachine(CLIENT);
        verify(calls).markAiOverflow(eq(ATTEMPT_ID), anyInt());
        verify(ari).startOutboundAiCall(CLIENT, CLIENT_NAME, AI_CALL);
        verify(ari, never()).originateToStasis(anyString(), anyString(), anyInt(), anyString());
        assertThat(dialer.findStats(CAMPAIGN_ID)).isEqualTo(new DialPacingStats(1, 1, 1));
    }

    @Test
    void operatorWhoDoesNotAnswerHandsTheClientToTheAi() {
        dialer.onClientAnswered(token, CLIENT, CLIENT_NAME);

        dialer.onChannelGone(OPERATOR, 19);

        verify(presence).markMissed(OPERATOR_ID);
        verify(ari).startOutboundAiCall(CLIENT, CLIENT_NAME, AI_CALL);
        verify(ari, never()).hangupChannel(CLIENT);
    }

    @Test
    void aiCallIsLeftToTeardown() {
        when(operatorPresence.status()).thenReturn(OperatorStatus.BREAK);
        dialer.onClientAnswered(token, CLIENT, CLIENT_NAME);

        dialer.onChannelGone(CLIENT, 16);

        verify(slots, never()).release(anyLong(), anyLong());
        verify(targets, never()).applyOutcome(anyLong(), anyLong(), any());
    }

    @Test
    void lateOperatorAnswerAfterTheAiTookOverIsHungUp() {
        dialer.onClientAnswered(token, CLIENT, CLIENT_NAME);
        dialer.onChannelGone(OPERATOR, 19);

        dialer.onOperatorAnswered(token, "operator-late");

        verify(ari).hangupChannel("operator-late");
        verify(ari, never()).bridgeWithRecording(anyString(), anyString(), anyLong(), anyString(), anyString(),
                anyString());
    }

    @Test
    void machineEndsTheCallAndGivesTheOperatorBack() {
        dialer.onClientAnswered(token, CLIENT, CLIENT_NAME);

        machineFound.run();

        verify(targets).applyOutcome(COMPANY_ID, TARGET_ID, Disposition.VOICEMAIL);
        verify(presence).releaseRinging(OPERATOR_ID);
        verify(ari).hangupChannel(OPERATOR);
        verify(ari).hangupChannel(CLIENT);
        verify(ari, never()).startOutboundAiCall(anyString(), anyString(), any());
    }

    @Test
    void clientWhoHangsUpWhileTheOperatorRingsIsAbandoned() {
        dialer.onClientAnswered(token, CLIENT, CLIENT_NAME);

        dialer.onChannelGone(CLIENT, 16);

        verify(calls).markAbandoned(eq(ATTEMPT_ID), anyInt());
        verify(targets).applyOutcome(COMPANY_ID, TARGET_ID, Disposition.HUNG_UP);
        verify(presence).releaseRinging(OPERATOR_ID);
        verify(ari).hangupChannel(OPERATOR);
        verify(slots).release(COMPANY_ID, CAMPAIGN_ID);
    }
}
