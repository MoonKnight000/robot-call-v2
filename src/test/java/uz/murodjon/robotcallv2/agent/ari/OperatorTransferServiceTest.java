package uz.murodjon.robotcallv2.agent.ari;

import ch.loway.oss.ari4java.ARI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.agent.dialog.DialogRouter;
import uz.murodjon.robotcallv2.agent.dialog.OperatorSnapshot;
import uz.murodjon.robotcallv2.agent.session.CallSession;
import uz.murodjon.robotcallv2.aiagent.AiAgentFixtures;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.operator.application.port.input.ContactCenterEventUseCase;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorAccountUseCase;
import uz.murodjon.robotcallv2.operator.domain.entity.CallOffer;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorSipAccount;
import uz.murodjon.robotcallv2.operator.domain.enums.ContactCenterEventType;
import uz.murodjon.robotcallv2.operator.infrastructure.config.OperatorProperties;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OperatorTransferServiceTest {

    private static final String CHANNEL_ID = "channel-123";
    private static final long COMPANY_ID = 5L;
    private static final long ATTEMPT_ID = 100L;
    private static final String PHONE = "+998901234567";

    private CallSessionRegistry callSessionRegistry;
    private FlowCallService flowCallService;
    private AriClient ariClient;
    private AsteriskProperties asteriskProperties;
    private OperatorProperties operatorProperties;
    private NotificationUseCase notificationService;
    private CallAttemptUseCase callAttemptUseCase;
    private DialogRouter dialogRouter;
    private OperatorAcceptance operatorAcceptance;
    private ContactCenterEventUseCase contactCenterEventUseCase;
    private OperatorAccountUseCase operatorAccountUseCase;

    private OperatorTransferService transferService;

    @BeforeEach
    void setUp() {
        callSessionRegistry = mock(CallSessionRegistry.class);
        flowCallService = mock(FlowCallService.class);
        ariClient = mock(AriClient.class);
        asteriskProperties = mock(AsteriskProperties.class);
        operatorProperties = mock(OperatorProperties.class);
        notificationService = mock(NotificationUseCase.class);
        callAttemptUseCase = mock(CallAttemptUseCase.class);
        dialogRouter = mock(DialogRouter.class);
        operatorAcceptance = mock(OperatorAcceptance.class);
        contactCenterEventUseCase = mock(ContactCenterEventUseCase.class);
        operatorAccountUseCase = mock(OperatorAccountUseCase.class);

        when(asteriskProperties.appName()).thenReturn("robot-call");
        when(asteriskProperties.buildOperatorDialString(anyString()))
                .thenAnswer(inv -> "PJSIP/" + inv.getArgument(0));

        when(operatorProperties.enabled()).thenReturn(true);
        when(operatorProperties.answerTimeoutSec()).thenReturn(30);

        when(callAttemptUseCase.findCompanyIdByAttemptId(ATTEMPT_ID)).thenReturn(COMPANY_ID);
        when(callAttemptUseCase.findPhoneByAttemptId(ATTEMPT_ID)).thenReturn(PHONE);

        ARI ari = mock(ARI.class, RETURNS_DEEP_STUBS);
        when(ariClient.requireConnection()).thenReturn(ari);

        transferService = new OperatorTransferService(
                callSessionRegistry,
                flowCallService,
                ariClient,
                asteriskProperties,
                operatorProperties,
                notificationService,
                callAttemptUseCase,
                dialogRouter,
                operatorAcceptance,
                contactCenterEventUseCase,
                operatorAccountUseCase
        );
    }

    @Test
    void transferToOperator_withSpecificOperator_publishesCallOfferToUserAndSendsNotification() {
        AiAgent agent = AiAgentFixtures.agent(1L, COMPANY_ID, 2L, "uz-UZ", null);
        CallSession session = new CallSession(
                CHANNEL_ID, COMPANY_ID, CHANNEL_ID + "-ext", "bridge-1", 10_000, null,
                ATTEMPT_ID, Instant.now().minusSeconds(20), "call.wav", "PJSIP/trunk-1", "trunk-1",
                0L, null, false, null
        ).withAgent(agent);

        when(callSessionRegistry.find(CHANNEL_ID)).thenReturn(session);

        OperatorSnapshot snapshot = new OperatorSnapshot(
                CHANNEL_ID, "Vali Karimov", "500000", "UZS", "2026-11-01", "D-123",
                "STAGE_2", "Mijoz operator talab qildi", "AI: Assalomu alaykum\nCLIENT: Operator kerak", null
        );
        when(dialogRouter.operatorSnapshot(CHANNEL_ID)).thenReturn(snapshot);

        OperatorSipAccount sipAccount = new OperatorSipAccount(
                42L, COMPANY_ID, "op-42", "pass", false, null, Instant.now(), Instant.now()
        );
        when(operatorAccountUseCase.findSipAccountByEndpoint("op-42")).thenReturn(sipAccount);

        transferService.transferToOperator(CHANNEL_ID, "op-42");

        // Verify Notification was sent
        verify(notificationService).notify(eq(COMPANY_ID), eq(NotificationType.OPERATOR_REQUEST),
                eq("Operatorga so'rov"), contains("Vali Karimov"), isNull());

        // Verify CallOffer screen pop was published directly to user
        ArgumentCaptor<CallOffer> offerCaptor = ArgumentCaptor.forClass(CallOffer.class);
        verify(contactCenterEventUseCase).publishToUser(eq(42L), eq(ContactCenterEventType.CALL_OFFER), offerCaptor.capture());

        CallOffer offer = offerCaptor.getValue();
        assertThat(offer.channelId()).isEqualTo(CHANNEL_ID);
        assertThat(offer.callAttemptId()).isEqualTo(ATTEMPT_ID);
        assertThat(offer.callerNumber()).isEqualTo(PHONE);
        assertThat(offer.clientName()).isEqualTo("Vali Karimov");
        assertThat(offer.summary()).contains("Vali Karimov").contains("500000 UZS");
        assertThat(offer.transcript()).isEqualTo(snapshot.transcript());
    }

    @Test
    void transferToOperator_whenTargetIsCompanyDefault_publishesCallOfferToCompany() {
        AiAgent agent = AiAgentFixtures.agent(1L, COMPANY_ID, 2L, "uz-UZ", null);
        CallSession session = new CallSession(
                CHANNEL_ID, COMPANY_ID, CHANNEL_ID + "-ext", "bridge-1", 10_000, null,
                ATTEMPT_ID, Instant.now().minusSeconds(10), "call.wav", "PJSIP/trunk-1", "trunk-1",
                0L, null, false, null
        ).withAgent(agent);

        when(callSessionRegistry.find(CHANNEL_ID)).thenReturn(session);
        when(operatorProperties.endpoint()).thenReturn("PJSIP/general-support");
        when(operatorAccountUseCase.findSipAccountByEndpoint("PJSIP/general-support")).thenReturn(null);

        transferService.transferToOperator(CHANNEL_ID, null);

        // Verify CallOffer is broadcast to company
        verify(contactCenterEventUseCase).publishToCompany(eq(COMPANY_ID), eq(ContactCenterEventType.CALL_OFFER), any(CallOffer.class));
    }

    @Test
    void transferToOperator_whenTransferQueueIdConfigured_delegatesToQueue() {
        // Agent with transferQueueId configured
        AiAgent baseAgent = AiAgentFixtures.agent(1L, COMPANY_ID, 2L, "uz-UZ", null);
        AiAgent agent = baseAgent.toBuilder()
                .callBehaviour(new uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentCallBehaviour(
                        uz.murodjon.robotcallv2.aiagent.domain.enums.InterruptionSensitivity.MEDIUM, 700, false,
                        uz.murodjon.robotcallv2.aiagent.domain.enums.VoicemailAction.LEAVE_MESSAGE,
                        null, false, null, null, null, 999L, null
                ))
                .build();

        CallSession session = new CallSession(
                CHANNEL_ID, COMPANY_ID, CHANNEL_ID + "-ext", "bridge-1", 10_000, null,
                ATTEMPT_ID, Instant.now(), "call.wav", "PJSIP/trunk-1", "trunk-1",
                0L, null, false, null
        ).withAgent(agent);

        when(callSessionRegistry.find(CHANNEL_ID)).thenReturn(session);

        transferService.transferToOperator(CHANNEL_ID, null);

        verify(flowCallService).transferCallToQueue(eq(COMPANY_ID), eq(CHANNEL_ID), eq(999L));
        verifyNoInteractions(contactCenterEventUseCase);
    }

    @Test
    void transferToOperator_whenSessionNotFound_hangsUp() {
        when(callSessionRegistry.find("missing-channel")).thenReturn(null);

        transferService.transferToOperator("missing-channel");

        verify(ariClient).hangup("missing-channel");
        verifyNoInteractions(contactCenterEventUseCase);
    }
}
