package uz.murodjon.robotcallv2.webhook.application.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.webhook.application.dto.WebhookDeliveryRow;
import uz.murodjon.robotcallv2.webhook.domain.entity.WebhookDelivery;

@Component
public class WebhookDeliveryMapper {

    public WebhookDeliveryRow toWebhookDeliveryRow(WebhookDelivery delivery) {
        return new WebhookDeliveryRow(
                delivery.id(),
                delivery.deliveryId(),
                delivery.source(),
                delivery.host(),
                delivery.status(),
                delivery.attempts(),
                delivery.nextAttemptAt(),
                delivery.lastError(),
                delivery.createdAt(),
                delivery.deliveredAt());
    }
}
