package uz.murodjon.robotcallv2.callrecord.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.agent.dialog.CallSummary;
import uz.murodjon.robotcallv2.aiagent.AiAgentFixtures;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AgentWebhookUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentWebhookConfig;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.PostCallAction;
import uz.murodjon.robotcallv2.aiagent.domain.enums.PostCallActionType;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.secret.application.port.input.SecretUseCase;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.dialog.Sentiment;
import uz.murodjon.robotcallv2.sms.application.dto.SmsSendRequest;
import uz.murodjon.robotcallv2.sms.application.port.input.SmsUseCase;
import uz.murodjon.robotcallv2.webhook.application.port.input.WebhookDeliveryUseCase;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PostCallActionExecutorTest {

    private static final long COMPANY_ID = 10L;
    private static final long CALL_ATTEMPT_ID = 99L;
    private static final String PHONE = "+998901234567";

    private SmsUseCase smsUseCase;
    private SecretUseCase secretUseCase;
    private NotificationUseCase notificationService;
    private AgentWebhookUseCase agentWebhookUseCase;
    private WebhookDeliveryUseCase webhookDelivery;
    private PostCallActionExecutor executor;

    @BeforeEach
    void setUp() {
        smsUseCase = mock(SmsUseCase.class);
        secretUseCase = mock(SecretUseCase.class);
        notificationService = mock(NotificationUseCase.class);
        agentWebhookUseCase = mock(AgentWebhookUseCase.class);
        webhookDelivery = mock(WebhookDeliveryUseCase.class);

        when(secretUseCase.resolveSecrets(eq(COMPANY_ID), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        executor = new PostCallActionExecutor(
                smsUseCase,
                secretUseCase,
                notificationService,
                agentWebhookUseCase,
                webhookDelivery
        );
    }

    @Test
    void sendsSmsOnMissedCallWithoutSummaryWhenTriggerMatches() {
        PostCallAction action = new PostCallAction(
                "action-1",
                "Missed call follow-up",
                PostCallActionType.SEND_SMS,
                Disposition.NO_ANSWER,
                false,
                "Sizga qo'ng'iroq qildik ({{phone}}), natija: {{disposition}}. {{summary}}",
                null,
                Map.of(),
                true
        );

        AiAgent agent = AiAgentFixtures.agent(1L, COMPANY_ID, 2L, "uz-UZ", null)
                .withPostCallActions(List.of(action));

        executor.executeActions(COMPANY_ID, CALL_ATTEMPT_ID, PHONE, agent, Disposition.NO_ANSWER, null);

        ArgumentCaptor<SmsSendRequest> captor = ArgumentCaptor.forClass(SmsSendRequest.class);
        verify(smsUseCase).sendSms(eq(COMPANY_ID), captor.capture());

        SmsSendRequest request = captor.getValue();
        assertEquals(PHONE, request.phone());
        assertEquals("Sizga qo'ng'iroq qildik (+998901234567), natija: NO_ANSWER. ", request.message());
    }

    @Test
    void doesNotSendSmsWhenOnlyOnSuccessIsTrueAndCallWasMissed() {
        PostCallAction action = new PostCallAction(
                "action-2",
                "Success only",
                PostCallActionType.SEND_SMS,
                null,
                true,
                "Rahmat, {{phone}}!",
                null,
                Map.of(),
                true
        );

        AiAgent agent = AiAgentFixtures.agent(1L, COMPANY_ID, 2L, "uz-UZ", null)
                .withPostCallActions(List.of(action));

        executor.executeActions(COMPANY_ID, CALL_ATTEMPT_ID, PHONE, agent, Disposition.NO_ANSWER, null);

        verify(smsUseCase, never()).sendSms(anyLong(), any());
    }

    @Test
    void sendsSmsWithFullSummaryOnSuccessfulCall() {
        PostCallAction action = new PostCallAction(
                "action-3",
                "Conversion action",
                PostCallActionType.SEND_SMS,
                Disposition.PROMISE_TO_PAY,
                true,
                "Xulosa: {{summary}}, kayfiyat: {{sentiment}}, sana: {{promisedDate}}",
                null,
                Map.of(),
                true
        );

        AiAgent agent = AiAgentFixtures.agent(1L, COMPANY_ID, 2L, "uz-UZ", null)
                .withPostCallActions(List.of(action));

        CallSummary summary = new CallSummary(
                "Mijoz to'lashga rozi bo'ldi",
                Sentiment.POSITIVE,
                95,
                90,
                false,
                null,
                null,
                Map.of("promisedDate", "2026-11-01"),
                List.of()
        );

        executor.executeActions(COMPANY_ID, CALL_ATTEMPT_ID, PHONE, agent, Disposition.PROMISE_TO_PAY, summary);

        ArgumentCaptor<SmsSendRequest> captor = ArgumentCaptor.forClass(SmsSendRequest.class);
        verify(smsUseCase).sendSms(eq(COMPANY_ID), captor.capture());

        SmsSendRequest request = captor.getValue();
        assertEquals("Xulosa: Mijoz to'lashga rozi bo'ldi, kayfiyat: POSITIVE, sana: 2026-11-01", request.message());
    }

    @Test
    void triggersWebhookEvenWhenSummaryIsNullForMissedCall() {
        when(webhookDelivery.deliver(anyLong(), anyString(), anyString(), anyMap(), anyMap())).thenReturn(true);

        PostCallAction action = new PostCallAction(
                "action-4",
                "Missed call webhook",
                PostCallActionType.WEBHOOK,
                Disposition.BUSY,
                false,
                null,
                "https://api.example.com/missed-call",
                Map.of("X-Test", "test-val"),
                true
        );

        AiAgent agent = AiAgentFixtures.agent(1L, COMPANY_ID, 2L, "uz-UZ", null)
                .withPostCallActions(List.of(action));

        executor.executeActions(COMPANY_ID, CALL_ATTEMPT_ID, PHONE, agent, Disposition.BUSY, null);

        verify(webhookDelivery).deliver(eq(COMPANY_ID), eq("POST_CALL_ACTION"), eq("https://api.example.com/missed-call"),
                anyMap(), argThat(payload ->
                        "BUSY".equals(payload.get("disposition")) &&
                        PHONE.equals(payload.get("phone")) &&
                        !payload.containsKey("summary")));
    }
}
