package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallEventRepository;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallEvent;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallEventEntity;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallEventJpaRepository;

import java.util.List;
import java.util.Map;

@Component
public class CallEventRepositoryAdapter implements CallEventRepository {

    private final ObjectMapper objectMapper;
    private static final TypeReference<Map<String, Object>> PAYLOAD = new TypeReference<>() {
    };

    private final CallEventJpaRepository jpaRepository;

    public CallEventRepositoryAdapter(CallEventJpaRepository jpaRepository, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void create(CallEvent event) {
        CallEventEntity entity = new CallEventEntity();
        entity.setCompanyId(event.companyId());
        entity.setCallAttemptId(event.callAttemptId());
        entity.setAt(event.at());
        entity.setType(event.type());
        try {
            entity.setPayload(event.payload().isEmpty() ? null : objectMapper.writeValueAsString(event.payload()));
        } catch (JsonProcessingException e) {
            entity.setPayload(null);
        }
        jpaRepository.save(entity);
    }

    @Override
    public List<CallEvent> findByCallAttemptId(long companyId, long callAttemptId) {
        return jpaRepository.findByCompanyIdAndCallAttemptIdOrderByAtAscIdAsc(companyId, callAttemptId).stream()
                .map(e -> new CallEvent(e.getId(), e.getCompanyId(), e.getCallAttemptId(), e.getAt(), e.getType(),
                        readPayload(e.getPayload())))
                .toList();
    }

    private Map<String, Object> readPayload(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, PAYLOAD);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
