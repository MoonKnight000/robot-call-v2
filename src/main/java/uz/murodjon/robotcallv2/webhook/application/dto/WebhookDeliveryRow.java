package uz.murodjon.robotcallv2.webhook.application.dto;

import uz.murodjon.robotcallv2.webhook.domain.enums.WebhookDeliveryStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * A delivery as the panel shows it: the host only, never the URL, headers or payload —
 * they carry credentials and the caller's data.
 */
public record WebhookDeliveryRow(
        long id,
        UUID deliveryId,
        String source,
        String host,
        WebhookDeliveryStatus status,
        int attempts,
        Instant nextAttemptAt,
        String lastError,
        Instant createdAt,
        Instant deliveredAt
) {
}
