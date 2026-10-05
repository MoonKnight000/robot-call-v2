package uz.murodjon.robotcallv2.webhook.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.notification.application.service.NotificationService;
import uz.murodjon.robotcallv2.secret.application.port.input.SecretUseCase;
import uz.murodjon.robotcallv2.shared.util.SecretCipher;
import uz.murodjon.robotcallv2.webhook.application.port.output.WebhookDeliveryRepository;
import uz.murodjon.robotcallv2.webhook.application.port.output.WebhookSenderPort;
import uz.murodjon.robotcallv2.webhook.domain.entity.WebhookDelivery;
import uz.murodjon.robotcallv2.webhook.domain.enums.WebhookDeliveryStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A receiver deduplicates by {@code X-RobotCall-Delivery}, so the id must survive into the
 * retry; and a retry is a fresh send to an address whose DNS may have moved, so it is
 * screened again.
 */
class WebhookDeliveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-14T10:00:00Z");
    private static final long COMPANY_ID = 1L;
    private static final String PUBLIC_URL = "https://8.8.8.8/hook";

    private final WebhookSenderPort sender = mock(WebhookSenderPort.class);
    private final WebhookDeliveryRepository repository = mock(WebhookDeliveryRepository.class);
    private final SecretUseCase secretUseCase = mock(SecretUseCase.class);
    private final SecretCipher cipher = mock(SecretCipher.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final WebhookDeliveryService service = new WebhookDeliveryService(sender, repository, secretUseCase,
            cipher, notificationService, Clock.fixed(NOW, ZoneOffset.UTC), new ObjectMapper());

    @BeforeEach
    void setUp() {
        when(secretUseCase.findDecryptedSecrets(COMPANY_ID)).thenReturn(Map.of());
    }

    @Test
    void aFailedFirstTryKeepsTheDeliveryIdItWasSentWith() throws Exception {
        doThrow(new RuntimeException("503 Service Unavailable"))
                .when(sender).post(any(), any(), any(), any(), any());
        when(cipher.available()).thenReturn(true);
        when(cipher.encrypt(anyString())).thenReturn("sealed");

        boolean delivered = service.deliver(COMPANY_ID, "POST_CALL", PUBLIC_URL, Map.of(), Map.of("event", "CALL_ENDED"));

        assertThat(delivered).isFalse();
        ArgumentCaptor<String> sentId = ArgumentCaptor.forClass(String.class);
        verify(sender).post(eq(PUBLIC_URL), any(), any(), isNull(), sentId.capture());
        ArgumentCaptor<WebhookDelivery> stored = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(repository).create(stored.capture());
        assertThat(stored.getValue().deliveryId().toString()).isEqualTo(sentId.getValue());
    }

    @Test
    void aRetryIsSentWithTheStoredDeliveryId() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        dueDelivery(deliveryId, PUBLIC_URL);

        service.retryDue();

        verify(sender).post(eq(PUBLIC_URL), eq(Map.of()), eq(Map.of()), isNull(), eq(deliveryId.toString()));
        verify(repository).markDelivered(5L, 2, NOW);
    }

    @Test
    void aRetryToAnAddressInsideTheNetworkIsNotSent() throws Exception {
        dueDelivery(UUID.randomUUID(), "http://127.0.0.1:8080/actuator");

        service.retryDue();

        verifyNoInteractions(sender);
        verify(repository).markRetry(eq(5L), eq(2), any(), contains("public"));
    }

    /** A target sealed under a key that has since been rotated away fails its try; the sweep goes on. */
    @Test
    void aTargetThatNoLongerDecryptsCountsAsAFailedTry() throws Exception {
        dueDelivery(UUID.randomUUID(), PUBLIC_URL);
        when(cipher.decrypt("sealed")).thenThrow(new IllegalStateException("no configured key matches"));

        service.retryDue();

        verifyNoInteractions(sender);
        verify(repository).markRetry(eq(5L), eq(2), any(), contains("could not be decrypted"));
    }

    private void dueDelivery(UUID deliveryId, String url) throws Exception {
        WebhookDelivery due = new WebhookDelivery(5L, deliveryId, COMPANY_ID, "POST_CALL", "hook.example", "sealed",
                Map.of(), WebhookDeliveryStatus.PENDING, 1, NOW, "503", NOW, null);
        when(repository.findDue(eq(NOW), anyInt())).thenReturn(List.of(due));
        when(repository.claim(eq(5L), eq(NOW), any())).thenReturn(true);
        when(cipher.decrypt("sealed")).thenReturn("{\"url\":\"" + url + "\",\"headers\":{}}");
    }
}
