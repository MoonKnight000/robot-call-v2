package uz.murodjon.robotcallv2.memory.application.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.memory.domain.entity.RememberedCall;
import uz.murodjon.robotcallv2.memory.infrastructure.persistence.entity.ClientMemoryEntity;

import java.util.List;
import java.util.Map;

/**
 * Entity to domain and back, including the two JSONB columns ({@code recent_calls},
 * {@code facts}). A column that fails to parse is read as empty rather than failing
 * the call that wanted the memory: a corrupt note must never block dialing.
 */
@Component
public class ClientMemoryMapper {

    private static final Logger log = LoggerFactory.getLogger(ClientMemoryMapper.class);
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final TypeReference<List<RememberedCall>> CALLS = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> FACTS = new TypeReference<>() {
    };

    public ClientMemory toClientMemory(ClientMemoryEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ClientMemory(
                entity.getId(),
                entity.getCompanyId(),
                entity.getPhone(),
                entity.getPreferredName(),
                entity.getPreferredLanguage(),
                entity.getOperatorNotes(),
                read(entity.getRecentCalls(), CALLS, List.of()),
                read(entity.getFacts(), FACTS, Map.of()),
                entity.getUpdatedAt());
    }

    public void applyToEntity(ClientMemory memory, ClientMemoryEntity entity) {
        entity.setPhone(memory.phone());
        entity.setPreferredName(memory.preferredName());
        entity.setPreferredLanguage(memory.preferredLanguage());
        entity.setOperatorNotes(memory.operatorNotes());
        entity.setRecentCalls(write(memory.recentCalls(), "[]"));
        entity.setFacts(write(memory.facts(), "{}"));
        entity.setUpdatedAt(memory.updatedAt());
    }

    private static <T> T read(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return JSON.readValue(json, type);
        } catch (Exception e) {
            log.warn("client_memory column is not valid JSON, reading it as empty: {}", e.getMessage());
            return fallback;
        }
    }

    private static String write(Object value, String fallback) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("client_memory value could not be serialized, storing empty: {}", e.getMessage());
            return fallback;
        }
    }
}
