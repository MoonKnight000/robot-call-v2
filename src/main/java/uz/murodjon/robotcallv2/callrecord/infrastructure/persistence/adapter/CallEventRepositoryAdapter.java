package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallEventRepository;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallEvent;
import uz.murodjon.robotcallv2.callrecord.domain.enums.CallEventType;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallEventEntity;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallEventJpaRepository;

import java.time.Instant;
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
        entity.setCallAttemptId(event.callAttemptId() > 0 ? event.callAttemptId() : null);
        entity.setChatSessionId(event.chatSessionId() > 0 ? event.chatSessionId() : null);
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
                .map(this::toCallEvent)
                .toList();
    }

    @Override
    public List<CallEvent> findByChatSessionId(long companyId, long chatSessionId) {
        return jpaRepository.findByCompanyIdAndChatSessionIdOrderByAtAscIdAsc(companyId, chatSessionId).stream()
                .map(this::toCallEvent)
                .toList();
    }

    @Override
    public List<CallEvent> findByCompanyIdAndType(long companyId, CallEventType type, Instant from, Instant to,
                                                  int limit) {
        return jpaRepository.findByCompanyIdAndTypeAndAtBetweenOrderByAtDesc(companyId, type, from, to,
                        PageRequest.of(0, limit)).stream()
                .map(this::toCallEvent)
                .toList();
    }

    private CallEvent toCallEvent(CallEventEntity e) {
        return new CallEvent(e.getId(), e.getCompanyId(),
                e.getCallAttemptId() != null ? e.getCallAttemptId() : 0,
                e.getChatSessionId() != null ? e.getChatSessionId() : 0,
                e.getAt(), e.getType(), readPayload(e.getPayload()));
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

    @Override
    public int deleteCreatedBefore(java.time.Instant cutoff, int batchSize) {
        return jpaRepository.deleteCreatedBefore(cutoff, batchSize);
    }
}
