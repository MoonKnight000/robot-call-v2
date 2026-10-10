package uz.murodjon.robotcallv2.webhook.application.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Live delivery event streamed to developers listening on the webhook event stream.
 */
public record WebhookLiveEvent(
        UUID deliveryId,
        long companyId,
        String source,
        String url,
        Map<String, String> headers,
        Map<String, Object> payload,
        Instant timestamp,
        boolean success,
        String error
) {
}
