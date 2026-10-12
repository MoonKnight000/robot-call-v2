package uz.murodjon.robotcallv2.webhook.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.notification.application.service.NotificationService;
import uz.murodjon.robotcallv2.secret.application.port.input.SecretUseCase;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.util.SecretCipher;
import uz.murodjon.robotcallv2.webhook.application.dto.WebhookDeliveryRow;
import uz.murodjon.robotcallv2.webhook.application.mapper.WebhookDeliveryMapper;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
            cipher, notificationService, Clock.fixed(NOW, ZoneOffset.UTC), new ObjectMapper(),
            new WebhookDeliveryMapper());

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

    /** A manual resend keeps the delivery id, so a receiver that got an earlier try after all can still deduplicate. */
    @Test
    void aManualResendOfAFailedDeliveryKeepsItsDeliveryIdAndCountsTheTry() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        WebhookDelivery failed = storedDelivery(deliveryId, WebhookDeliveryStatus.FAILED, 4);
        when(repository.findByCompanyIdAndId(COMPANY_ID, 5L))
                .thenReturn(failed, storedDelivery(deliveryId, WebhookDeliveryStatus.DELIVERED, 5));
        when(repository.claimFailed(eq(5L), any())).thenReturn(true);
        when(cipher.decrypt("sealed")).thenReturn("{\"url\":\"" + PUBLIC_URL + "\",\"headers\":{}}");

        WebhookDeliveryRow row = service.retryDelivery(COMPANY_ID, 5L);

        verify(sender).post(eq(PUBLIC_URL), eq(Map.of()), eq(Map.of()), isNull(), eq(deliveryId.toString()));
        verify(repository).markDelivered(5L, 5, NOW);
        assertThat(row.status()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
    }

    /** Whoever pressed the button sees the failure in the answer; it is not queued or notified again. */
    @Test
    void aManualResendThatFailsAgainIsMarkedFailedWithoutANotification() throws Exception {
        WebhookDelivery failed = storedDelivery(UUID.randomUUID(), WebhookDeliveryStatus.FAILED, 4);
        when(repository.findByCompanyIdAndId(COMPANY_ID, 5L)).thenReturn(failed);
        when(repository.claimFailed(eq(5L), any())).thenReturn(true);
        when(cipher.decrypt("sealed")).thenReturn("{\"url\":\"" + PUBLIC_URL + "\",\"headers\":{}}");
        doThrow(new RuntimeException("502 Bad Gateway")).when(sender).post(any(), any(), any(), any(), any());

        service.retryDelivery(COMPANY_ID, 5L);

        verify(repository).markFailed(eq(5L), eq(5), contains("502"));
        verify(repository, never()).markRetry(anyLong(), anyInt(), any(), any());
        verifyNoInteractions(notificationService);
    }

    /** A delivery the sweep still owns is not sent from the panel as well. */
    @Test
    void onlyAFailedDeliveryCanBeResent() {
        WebhookDelivery pending = storedDelivery(UUID.randomUUID(), WebhookDeliveryStatus.PENDING, 2);
        when(repository.findByCompanyIdAndId(COMPANY_ID, 5L)).thenReturn(pending);

        assertThatThrownBy(() -> service.retryDelivery(COMPANY_ID, 5L)).isInstanceOf(ConflictException.class);

        verifyNoInteractions(sender);
        verify(repository, never()).claimFailed(anyLong(), any());
    }

    @Test
    void deliveryPublishesToSubscriber() throws Exception {
        java.util.List<String> receivedEvents = new java.util.concurrent.CopyOnWriteArrayList<>();
        uz.murodjon.robotcallv2.shared.stream.EventSink sink = new uz.murodjon.robotcallv2.shared.stream.EventSink() {
            @Override
            public void send(String eventName, Object payload) {
                receivedEvents.add(eventName);
            }

            @Override
            public void sendHeartbeat() {
            }
        };

        uz.murodjon.robotcallv2.shared.stream.EventSubscription sub = service.subscribe(COMPANY_ID, sink);
        assertThat(receivedEvents).contains("connected");

        service.deliver(COMPANY_ID, "POST_CALL", PUBLIC_URL, Map.of(), Map.of("test", 1));

        Thread.sleep(80);
        assertThat(receivedEvents).contains("webhook");
        sub.cancel();
    }

    private static WebhookDelivery storedDelivery(UUID deliveryId, WebhookDeliveryStatus status, int attempts) {
        return new WebhookDelivery(5L, deliveryId, COMPANY_ID, "POST_CALL", "hook.example", "sealed",
                Map.of(), status, attempts, null, "503", NOW, null);
    }

    private void dueDelivery(UUID deliveryId, String url) throws Exception {
        WebhookDelivery due = new WebhookDelivery(5L, deliveryId, COMPANY_ID, "POST_CALL", "hook.example", "sealed",
                Map.of(), WebhookDeliveryStatus.PENDING, 1, NOW, "503", NOW, null);
        when(repository.findDue(eq(NOW), anyInt())).thenReturn(List.of(due));
        when(repository.claim(eq(5L), eq(NOW), any())).thenReturn(true);
        when(cipher.decrypt("sealed")).thenReturn("{\"url\":\"" + url + "\",\"headers\":{}}");
    }
}
