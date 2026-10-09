package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentEvalResult;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentEvalRun;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentTestCase;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AgentEvalResultEntity;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AgentEvalRunEntity;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AgentTestCaseEntity;

import java.util.List;
import java.util.Map;

/** Maps the evaluation entities to their domain records; the list and map columns are JSON. */
@Component
public class AgentEvaluationEntityMapper {

    private final ObjectMapper objectMapper;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> STRING_MAP_TYPE = new TypeReference<>() {};

    public AgentEvaluationEntityMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AgentTestCase toAgentTestCase(AgentTestCaseEntity entity) {
        if (entity == null) {
            return null;
        }
        return new AgentTestCase(entity.getId(), entity.getCompanyId(), entity.getAgentId(), entity.getName(),
                entity.getPersonaPrompt(), read(entity.getFacts(), MAP_TYPE, Map.of()), entity.getLanguage(),
                entity.getExpectDisposition(), entity.getExpectStage(),
                read(entity.getMustSay(), LIST_TYPE, List.of()), read(entity.getMustNotSay(), LIST_TYPE, List.of()),
                entity.getMaxTurns(), entity.getCreatedAt(), read(entity.getToolMocks(), STRING_MAP_TYPE, Map.of()),
                read(entity.getExpectTools(), LIST_TYPE, List.of()));
    }

    public AgentEvalRun toAgentEvalRun(AgentEvalRunEntity entity) {
        if (entity == null) {
            return null;
        }
        return new AgentEvalRun(entity.getId(), entity.getCompanyId(), entity.getAgentId(), entity.getAgentVersion(),
                entity.getMode(), entity.getConfigHash(), entity.getStatus(), entity.getTotal(), entity.getPassed(),
                entity.getAvgQaScore(), entity.getError(),
                entity.getStartedAt(), entity.getFinishedAt(), entity.getRepeatCount());
    }

    public AgentEvalResult toAgentEvalResult(AgentEvalResultEntity entity) {
        if (entity == null) {
            return null;
        }
        return new AgentEvalResult(entity.getId(), entity.getRunId(), entity.getTestCaseId(), entity.getCaseName(),
                entity.isPassed(), entity.getDisposition(), entity.getStage(),
                read(entity.getProblems(), LIST_TYPE, List.of()), entity.getTranscript(), entity.getQaScore(),
                entity.isGuardrailViolation(), entity.getAttempt());
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            // Cannot happen: only lists and maps of plain values are written here.
            throw new IllegalStateException("Evaluation column could not be serialised", e);
        }
    }

    private <T> T read(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return fallback;
        }
    }
}
