package uz.murodjon.robotcallv2.notification.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.notification.application.port.output.NotificationSenderPort;
import uz.murodjon.robotcallv2.notification.application.port.output.NotificationSettingsRepository;
import uz.murodjon.robotcallv2.notification.domain.entity.NotificationChannel;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationChannelType;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.secret.application.port.input.SecretUseCase;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

class NotificationDispatchServiceTest {

    private static final long COMPANY = 7L;
    private static final NotificationType TYPE = NotificationType.MISSED_CALL;

    private NotificationSettingsRepository repository;
    private SecretUseCase secretUseCase;
    private NotificationSenderPort emailSender;
    private NotificationSenderPort webhookSender;
    private NotificationDispatchService service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationSettingsRepository.class);
        secretUseCase = mock(SecretUseCase.class);
        emailSender = sender(NotificationChannelType.EMAIL);
        webhookSender = sender(NotificationChannelType.WEBHOOK);
        service = new NotificationDispatchService(repository, secretUseCase, List.of(emailSender, webhookSender));
    }

    @Test
    void aWebhookIsSignedWithTheCompanySecret() {
        when(repository.findEnabledChannelsByCompanyIdAndType(COMPANY, TYPE)).thenReturn(List.of(
                new NotificationChannel(NotificationChannelType.WEBHOOK, "https://example.com/hook", true)));
        when(secretUseCase.findDecryptedSecrets(COMPANY)).thenReturn(Map.of("WEBHOOK_SIGNING_SECRET", "s3cret"));

        service.dispatch(COMPANY, TYPE, "title", "message", null);

        verify(webhookSender).send("https://example.com/hook", TYPE, "title", "message", null, "s3cret");
    }

    /** The secret is decrypted only when a webhook is about to use it. */
    @Test
    void secretsAreNotReadWithoutAWebhookChannel() {
        when(repository.findEnabledChannelsByCompanyIdAndType(COMPANY, TYPE)).thenReturn(List.of(
                new NotificationChannel(NotificationChannelType.EMAIL, "ops@example.com", true)));

        service.dispatch(COMPANY, TYPE, "title", "message", null);

        verify(emailSender).send("ops@example.com", TYPE, "title", "message", null, null);
        verifyNoInteractions(secretUseCase);
    }

    @Test
    void aChannelWithoutASenderInThisBuildIsSkipped() {
        when(repository.findEnabledChannelsByCompanyIdAndType(COMPANY, TYPE)).thenReturn(List.of(
                new NotificationChannel(NotificationChannelType.TELEGRAM, "@ops", true)));

        service.dispatch(COMPANY, TYPE, "title", "message", null);

        verify(emailSender, never()).send(any(), any(), any(), any(), any(), any());
        verify(webhookSender, never()).send(any(), any(), any(), any(), any(), any());
    }

    private static NotificationSenderPort sender(NotificationChannelType type) {
        NotificationSenderPort sender = mock(NotificationSenderPort.class);
        when(sender.resolveChannelType()).thenReturn(type);
        return sender;
    }
}
