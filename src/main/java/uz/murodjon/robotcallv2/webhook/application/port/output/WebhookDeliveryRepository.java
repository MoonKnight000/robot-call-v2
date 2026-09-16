package uz.murodjon.robotcallv2.webhook.application.port.output;

import uz.murodjon.robotcallv2.webhook.domain.entity.WebhookDelivery;

import java.time.Instant;
import java.util.List;

public interface WebhookDeliveryRepository {

    long create(WebhookDelivery delivery);

    /** Pending deliveries whose next try is due, oldest first. */
    List<WebhookDelivery> findDue(Instant now, int limit);

    /**
     * Takes a due delivery for this instance by pushing its next try to {@code lease};
     * false when another instance took it first.
     */
    boolean claim(long id, Instant now, Instant lease);

    void markDelivered(long id, int attempts, Instant deliveredAt);

    void markRetry(long id, int attempts, Instant nextAttemptAt, String error);

    void markFailed(long id, int attempts, String error);

    int deleteDeliveredBefore(Instant cutoff);
}
