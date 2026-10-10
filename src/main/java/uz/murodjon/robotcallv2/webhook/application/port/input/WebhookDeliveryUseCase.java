package uz.murodjon.robotcallv2.webhook.application.port.input;

import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.stream.EventSink;
import uz.murodjon.robotcallv2.shared.stream.EventSubscription;
import uz.murodjon.robotcallv2.webhook.application.dto.WebhookDeliveryRow;
import uz.murodjon.robotcallv2.webhook.domain.entity.WebhookDeliveryFilter;

import java.util.Map;

/**
 * Sends a webhook that must not be lost (NEXT-PLAN S.6): the first try happens now, and a
 * failure is kept and tried again later rather than ending in a log line.
 */
public interface WebhookDeliveryUseCase {

    /**
     * POSTs {@code payload} to {@code url}, signed with the company's webhook secret when it
     * has one.
     *
     * @param source  what is sending it, stored with a retry: {@code POST_CALL}, {@code POST_CALL_ACTION}
     * @param url     the resolved address — {@code {{secrets.*}}} already filled in
     * @param headers the resolved headers
     * @return whether it was delivered now; false when it failed and was queued for retry
     */
    boolean deliver(long companyId, String source, String url, Map<String, String> headers,
                    Map<String, Object> payload);

    /**
     * POSTs {@code payload} once and returns the JSON reply — for a caller that needs the
     * answer now. Nothing is kept for retry.
     */
    Map<String, Object> postForReply(String url, Map<String, String> headers, Map<String, Object> payload,
                                     String signingSecret);

    /**
     * The deliveries kept for retry: every webhook whose first try failed, until a week
     * after it got through. A webhook delivered on its first try is not kept.
     */
    PageableData<WebhookDeliveryRow> filterDeliveries(long companyId, WebhookDeliveryFilter filter);

    /**
     * Sends a {@code FAILED} delivery once more, now, with the same {@code X-RobotCall-Delivery}
     * id. The returned row says how it went: {@code DELIVERED}, or {@code FAILED} again with
     * the new {@code lastError}.
     */
    WebhookDeliveryRow retryDelivery(long companyId, long id);

    /**
     * Subscribes {@code sink} to the live webhook delivery stream for {@code companyId}.
     */
    EventSubscription subscribe(long companyId, EventSink sink);
}
