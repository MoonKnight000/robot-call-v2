package uz.murodjon.robotcallv2.webhook.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.webhook.domain.entity.WebhookDelivery;
import uz.murodjon.robotcallv2.webhook.infrastructure.persistence.entity.WebhookDeliveryEntity;

import java.util.Map;

/** Maps between {@link WebhookDeliveryEntity} and {@link WebhookDelivery}; the payload is JSON. */
@Component
public class WebhookDeliveryEntityMapper {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {};

    public WebhookDelivery toWebhookDelivery(WebhookDeliveryEntity entity) {
        if (entity == null) {
            return null;
        }
        return new WebhookDelivery(
                entity.getId(),
                entity.getDeliveryId(),
                entity.getCompanyId(),
                entity.getSource(),
                entity.getHost(),
                entity.getSealedTarget(),
                readPayload(entity.getPayload()),
                entity.getStatus(),
                entity.getAttempts(),
                entity.getNextAttemptAt(),
                entity.getLastError(),
                entity.getCreatedAt(),
                entity.getDeliveredAt());
    }

    public String writePayload(Map<String, Object> payload) {
        try {
            return JSON.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (Exception e) {
            throw new IllegalStateException("Webhook payload could not be serialised", e);
        }
    }

    private static Map<String, Object> readPayload(String json) {
        try {
            return json == null ? Map.of() : JSON.readValue(json, PAYLOAD_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Stored webhook payload could not be read", e);
        }
    }
}
