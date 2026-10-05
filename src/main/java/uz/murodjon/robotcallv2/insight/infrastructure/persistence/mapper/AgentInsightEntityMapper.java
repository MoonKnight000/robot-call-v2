package uz.murodjon.robotcallv2.insight.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.insight.domain.entity.AgentInsight;
import uz.murodjon.robotcallv2.insight.infrastructure.persistence.entity.AgentInsightEntity;

import java.time.Instant;
import java.util.List;

/** Maps the insight row to its domain record; the sample call ids are a JSON column. */
@Component
public class AgentInsightEntityMapper {

    private final ObjectMapper objectMapper;
    private static final TypeReference<List<Long>> ID_LIST = new TypeReference<>() {};

    public AgentInsightEntityMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AgentInsight toAgentInsight(AgentInsightEntity entity) {
        if (entity == null) {
            return null;
        }
        return new AgentInsight(entity.getId(), entity.getCompanyId(), entity.getAiAgentId(), entity.getType(),
                entity.getTitle(), entity.getDetail(), entity.getSuggestion(), entity.getCallCount(),
                readIds(entity.getSampleCallIds()), entity.getStatus(), entity.getDecisionReason(),
                entity.getDecidedBy(), entity.getDecidedAt(), entity.getEvalRunId(), entity.getCreatedAt());
    }

    /** {@code entity} filled from {@code insight}; a row without an id is a new proposal. */
    public AgentInsightEntity toAgentInsightEntity(AgentInsight insight, AgentInsightEntity entity) {
        entity.setId(insight.id());
        entity.setCompanyId(insight.companyId());
        entity.setAiAgentId(insight.aiAgentId());
        entity.setType(insight.type());
        entity.setTitle(insight.title());
        entity.setDetail(insight.detail());
        entity.setSuggestion(insight.suggestion());
        entity.setCallCount(insight.callCount());
        entity.setSampleCallIds(writeIds(insight.sampleCalls()));
        entity.setStatus(insight.status());
        entity.setDecisionReason(insight.decisionReason());
        entity.setDecidedBy(insight.decidedBy());
        entity.setDecidedAt(insight.decidedAt());
        entity.setEvalRunId(insight.evalRunId());
        entity.setCreatedAt(insight.createdAt() != null ? insight.createdAt() : Instant.now());
        return entity;
    }

    private List<Long> readIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, ID_LIST);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeIds(List<Long> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            // Cannot happen: a list of longs is always serialisable.
            throw new IllegalStateException("Insight sample call ids could not be serialised", e);
        }
    }
}
