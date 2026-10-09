package uz.murodjon.robotcallv2.agent.queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorAccountUseCase;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorPresenceUseCase;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorSipAccount;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;
import java.util.concurrent.ExecutorService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProgressiveDialerTest {

    private static final long COMPANY_ID = 1L;
    private static final long OPERATOR_ID = 20L;
    private static final long TARGET_ID = 300L;
    private static final long ATTEMPT_ID = 900L;
    private static final String PHONE = "998901112233";
    private static final String CLIENT = "client-1";
    private static final String OPERATOR = "operator-1";

    private AriService ari;
    private OperatorPresenceUseCase presence;
    private CallAttemptUseCase calls;
    private CampaignTargetUseCase targets;
    private ProgressiveDialer dialer;
    private String token;
    private Runnable machineFound;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ari = mock(AriService.class);
        presence = mock(OperatorPresenceUseCase.class);
        calls = mock(CallAttemptUseCase.class);
        targets = mock(CampaignTargetUseCase.class);
        ObjectProvider<AriService> ariProvider = mock(ObjectProvider.class);
        when(ariProvider.getObject()).thenReturn(ari);
        OperatorAccountUseCase accounts = mock(OperatorAccountUseCase.class);
        when(accounts.findSipAccount(COMPANY_ID, OPERATOR_ID)).thenReturn(new OperatorSipAccount(OPERATOR_ID,
                COMPANY_ID, "op-20", "secret", false, null, Instant.now(), Instant.now()));
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
        when(ari.bridgeWithRecording(CLIENT, "PJSIP/trunk-1", COMPANY_ID, PHONE, OPERATOR, "uz-UZ"))
                .thenReturn(ATTEMPT_ID);
        when(calls.findAttemptIdByChannel(CLIENT)).thenReturn(ATTEMPT_ID);
        dialer = new ProgressiveDialer(ariProvider, accounts, presence, calls, targets, executor);

        dialer.dial(COMPANY_ID, OPERATOR_ID, TARGET_ID, PHONE, "uz-UZ");
        ArgumentCaptor<String> appArgs = ArgumentCaptor.forClass(String.class);
        verify(ari).originateClientCall(eq(COMPANY_ID), eq(PHONE), appArgs.capture(), anyInt(), eq("uz-UZ"),
                eq(TARGET_ID));
        token = appArgs.getValue().substring("progressive-client,".length());
    }

    @Test
    void clientWhoNeverAnswersGivesTheOperatorBackAtOnce() {
        dialer.onChannelGone(CLIENT, 19);

        verify(presence).releaseRinging(OPERATOR_ID);
        verify(targets).applyOutcome(COMPANY_ID, TARGET_ID, Disposition.NO_ANSWER);
        verify(ari, never()).originateToStasis(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void answeredClientRingsTheOperatorAndIsWatchedForAMachine() {
        dialer.onClientAnswered(token, CLIENT, "PJSIP/trunk-1");

        verify(ari).watchForAnsweringMachine(eq(CLIENT), any());
        verify(ari).originateToStasis(eq("PJSIP/op-20"), eq("progressive-operator," + token), anyInt(), anyString());
    }

    @Test
    void operatorWhoAnswersIsJoinedToTheClient() {
        dialer.onClientAnswered(token, CLIENT, "PJSIP/trunk-1");

        dialer.onOperatorAnswered(token, OPERATOR);

        verify(presence).markOnCall(OPERATOR_ID, OPERATOR);
        verify(ari).bridgeWithRecording(CLIENT, "PJSIP/trunk-1", COMPANY_ID, PHONE, OPERATOR, "uz-UZ");
        verify(calls).markOperatorAnswered(eq(ATTEMPT_ID), eq(OPERATOR_ID), anyInt());
        verify(targets, never()).applyOutcome(anyLong(), anyLong(), any());
    }

    @Test
    void machineBeforeTheOperatorAnswersNeverReachesThem() {
        dialer.onClientAnswered(token, CLIENT, "PJSIP/trunk-1");

        machineFound.run();
        dialer.onOperatorAnswered(token, OPERATOR);

        verify(calls).finishUnansweredAttempt(CLIENT, Disposition.VOICEMAIL);
        verify(targets).applyOutcome(COMPANY_ID, TARGET_ID, Disposition.VOICEMAIL);
        verify(presence).releaseRinging(OPERATOR_ID);
        verify(ari, atLeastOnce()).hangupChannel(OPERATOR);
        verify(ari).hangupChannel(CLIENT);
        verify(ari, never()).bridgeWithRecording(anyString(), anyString(), anyLong(), anyString(), anyString(),
                anyString());
    }

    @Test
    void machineAfterTheOperatorJoinedEndsTheCallWithoutAWrapUp() {
        dialer.onClientAnswered(token, CLIENT, "PJSIP/trunk-1");
        dialer.onOperatorAnswered(token, OPERATOR);

        machineFound.run();
        dialer.onChannelGone(CLIENT, 16);
        dialer.onChannelGone(OPERATOR, 16);

        verify(ari).hangupChannelWithDisposition(CLIENT, Disposition.VOICEMAIL);
        verify(targets).applyOutcome(COMPANY_ID, TARGET_ID, Disposition.VOICEMAIL);
        verify(presence).markCallEnded(eq(OPERATOR_ID), anyInt());
        verify(presence).finishWrapUp(OPERATOR_ID);
    }

    @Test
    void operatorWhoRingsOutLeavesAnAbandonedCall() {
        dialer.onClientAnswered(token, CLIENT, "PJSIP/trunk-1");

        dialer.onChannelGone(OPERATOR, 19);

        verify(presence).markMissed(OPERATOR_ID);
        verify(calls).finishUnansweredAttempt(CLIENT, Disposition.FAILED);
        verify(calls).markAbandoned(eq(ATTEMPT_ID), anyInt());
        verify(targets).applyOutcome(COMPANY_ID, TARGET_ID, Disposition.FAILED);
        verify(ari).hangupChannel(CLIENT);
    }

    @Test
    void clientWhoHangsUpWhileTheOperatorRingsIsAbandonedAndTheOperatorFreed() {
        dialer.onClientAnswered(token, CLIENT, "PJSIP/trunk-1");

        dialer.onChannelGone(CLIENT, 16);
        dialer.onChannelGone(OPERATOR, 16);

        verify(ari).hangupChannel(OPERATOR);
        verify(presence).releaseRinging(OPERATOR_ID);
        verify(presence, never()).markMissed(anyLong());
        verify(calls).finishUnansweredAttempt(CLIENT, Disposition.HUNG_UP);
        verify(targets).applyOutcome(COMPANY_ID, TARGET_ID, Disposition.HUNG_UP);
    }

    @Test
    void targetIsSettledOnce() {
        dialer.onClientAnswered(token, CLIENT, "PJSIP/trunk-1");

        machineFound.run();
        dialer.onChannelGone(CLIENT, 16);

        verify(targets, times(1)).applyOutcome(anyLong(), anyLong(), any());
    }
}
