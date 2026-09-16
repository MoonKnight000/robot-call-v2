package uz.murodjon.robotcallv2.webhook.domain.entity;

import uz.murodjon.robotcallv2.webhook.domain.enums.WebhookDeliveryStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A webhook that failed its first try and is being tried again (NEXT-PLAN S.6).
 *
 * @param deliveryId   sent as {@code X-RobotCall-Delivery} on every try, the first included,
 *                     so a receiver can tell a retry from a new delivery
 * @param source       what sent it: {@code POST_CALL} (the agent's post-call webhook) or
 *                     {@code POST_CALL_ACTION} (a post-call {@code WEBHOOK} action)
 * @param host         the target's host, the only part of the address that may be shown
 * @param sealedTarget the resolved URL and headers, encrypted — they carry credentials
 * @param attempts     tries so far, the first included
 */
public record WebhookDelivery(
        Long id,
        UUID deliveryId,
        long companyId,
        String source,
        String host,
        String sealedTarget,
        Map<String, Object> payload,
        WebhookDeliveryStatus status,
        int attempts,
        Instant nextAttemptAt,
        String lastError,
        Instant createdAt,
        Instant deliveredAt
) {

    /** A delivery whose first try just failed; the adapter stamps id and createdAt. */
    public static WebhookDelivery failedFirstTry(long companyId, UUID deliveryId, String source, String host,
                                                 String sealedTarget, Map<String, Object> payload,
                                                 Instant nextAttemptAt, String error) {
        return new WebhookDelivery(null, deliveryId, companyId, source, host, sealedTarget, payload,
                WebhookDeliveryStatus.PENDING, 1, nextAttemptAt, error, null, null);
    }
}
