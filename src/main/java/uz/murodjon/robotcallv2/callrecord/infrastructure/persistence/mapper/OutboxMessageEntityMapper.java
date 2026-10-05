package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.callrecord.domain.entity.OutboxMessage;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.OutboxMessageEntity;

import java.util.Map;

@Component
public class OutboxMessageEntityMapper {

    private static final Logger log = LoggerFactory.getLogger(OutboxMessageEntityMapper.class);
    private final ObjectMapper objectMapper;
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    public OutboxMessageEntityMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OutboxMessage toOutboxMessage(OutboxMessageEntity entity) {
        return new OutboxMessage(
                entity.getId(),
                entity.getCompanyId(),
                entity.getKind(),
                entity.getCallAttemptId(),
                readPayload(entity.getId(), entity.getPayload()),
                entity.getIdempotencyKey(),
                entity.getStatus(),
                entity.getAttempts(),
                entity.getNextAttemptAt(),
                entity.getLastError(),
                entity.getCreatedAt(),
                entity.getSentAt());
    }

    public String toPayloadJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // A map of strings, numbers and booleans always serializes.
            throw new IllegalStateException("outbox payload is not serializable", e);
        }
    }

    /**
     * A payload that cannot be read comes back empty: its delivery then fails like any other
     * and the row ends DEAD, with an alert, instead of stopping the whole batch.
     */
    private Map<String, Object> readPayload(Long id, String json) {
        try {
            return objectMapper.readValue(json, PAYLOAD_TYPE);
        } catch (Exception e) {
            log.warn("Corrupt outbox_message.payload on message {}: {}", id, e.getMessage());
            return Map.of();
        }
    }
}
