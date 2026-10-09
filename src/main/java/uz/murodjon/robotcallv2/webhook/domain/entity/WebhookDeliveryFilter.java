package uz.murodjon.robotcallv2.webhook.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;
import uz.murodjon.robotcallv2.webhook.domain.enums.WebhookDeliveryStatus;
import uz.murodjon.robotcallv2.webhook.domain.enums.WebhookDeliveryTableField;

import java.time.Instant;
import java.util.LinkedHashMap;

/**
 * The webhook delivery log's page. Every criterion is optional; null means "any".
 *
 * @param source {@code POST_CALL} or {@code POST_CALL_ACTION}
 * @param from   inclusive
 * @param to     exclusive
 */
public record WebhookDeliveryFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<WebhookDeliveryTableField, Sort.Direction> orders,
        WebhookDeliveryStatus status,
        String source,
        Instant from,
        Instant to
) implements FilterInterface<WebhookDeliveryTableField> {

    public WebhookDeliveryFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(WebhookDeliveryTableField.CREATED_AT, Sort.Direction.DESC);
        }
    }
}
