package uz.murodjon.robotcallv2.webhook.application.port.output;

import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.webhook.domain.entity.WebhookDelivery;
import uz.murodjon.robotcallv2.webhook.domain.entity.WebhookDeliveryFilter;

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

    PageableData<WebhookDelivery> findPageByCompanyId(long companyId, WebhookDeliveryFilter filter);

    WebhookDelivery findByCompanyIdAndId(long companyId, long id);

    /**
     * Takes a {@code FAILED} delivery for a manual resend by making it {@code PENDING} with its
     * next try at {@code lease}, so the sweep leaves it alone; false when it is not failed any more.
     */
    boolean claimFailed(long id, Instant lease);
}
