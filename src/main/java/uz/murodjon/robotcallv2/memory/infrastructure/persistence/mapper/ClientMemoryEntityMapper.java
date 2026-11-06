package uz.murodjon.robotcallv2.memory.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.memory.domain.entity.MemorySource;
import uz.murodjon.robotcallv2.memory.domain.entity.RememberedCall;
import uz.murodjon.robotcallv2.memory.infrastructure.persistence.entity.ClientMemoryEntity;

import java.util.List;
import java.util.Map;

/**
 * Entity to domain and back, including the three JSONB columns ({@code recent_calls},
 * {@code facts}, {@code fact_sources}). A column that fails to parse is read as empty rather than failing
 * the call that wanted the memory: a corrupt note must never block dialing.
 */
@Component
public class ClientMemoryEntityMapper {

    private static final Logger log = LoggerFactory.getLogger(ClientMemoryEntityMapper.class);
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final TypeReference<List<RememberedCall>> CALLS = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> FACTS = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, MemorySource>> SOURCES = new TypeReference<>() {
    };

    public ClientMemory toClientMemory(ClientMemoryEntity entity) {
        if (entity == null) {
            return null;
        }
        return ClientMemory.builder()
                .id(entity.getId())
                .companyId(entity.getCompanyId())
                .phone(entity.getPhone())
                .preferredName(entity.getPreferredName())
                .preferredLanguage(entity.getPreferredLanguage())
                .operatorNotes(entity.getOperatorNotes())
                .recentCalls(read(entity.getRecentCalls(), CALLS, List.of()))
                .facts(read(entity.getFacts(), FACTS, Map.of()))
                .factSources(read(entity.getFactSources(), SOURCES, Map.of()))
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public void applyToEntity(ClientMemory memory, ClientMemoryEntity entity) {
        entity.setPhone(memory.phone());
        entity.setPreferredName(memory.preferredName());
        entity.setPreferredLanguage(memory.preferredLanguage());
        entity.setOperatorNotes(memory.operatorNotes());
        entity.setRecentCalls(write(memory.recentCalls(), "[]"));
        entity.setFacts(write(memory.facts(), "{}"));
        entity.setFactSources(write(memory.factSources(), "{}"));
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
