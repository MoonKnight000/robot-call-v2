package uz.murodjon.robotcallv2.scenario.application.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.infrastructure.persistence.entity.ScenarioEntity;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

@Component
public class ScenarioMapper {

    private static final Logger log = LoggerFactory.getLogger(ScenarioMapper.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    public ScenarioDefinition readDefinition(String json) {
        try {
            return JSON.readValue(json, ScenarioDefinition.class);
        } catch (Exception e) {
            log.error("Corrupt scenario definition JSON: {}", e.getMessage());
            throw new IllegalStateException("Corrupt scenario definition: " + e.getMessage(), e);
        }
    }

    public String writeDefinition(ScenarioDefinition definition) {
        try {
            return JSON.writeValueAsString(definition);
        } catch (Exception e) {
            throw new ValidationException(ErrorCode.SCENARIO_DEFINITION_INVALID, e.getMessage());
        }
    }

    public Scenario entityToDomain(ScenarioEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Scenario(
                entity.getId(),
                entity.getScenarioKey(),
                entity.getVersion(),
                entity.getName(),
                entity.getDescription(),
                entity.isBuiltin(),
                entity.isActive(),
                readDefinition(entity.getDefinition()),
                entity.getCreatedAt(),
                entity.getCreatedBy()
        );
    }
}
