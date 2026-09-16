package uz.murodjon.robotcallv2.scenario.infrastructure.persistence.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.infrastructure.persistence.entity.ScenarioEntity;

@Component
public class ScenarioEntityMapper {

    private static final Logger log = LoggerFactory.getLogger(ScenarioEntityMapper.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    public ScenarioDefinition readDefinition(String json) {
        try {
            return JSON.readValue(json, ScenarioDefinition.class);
        } catch (Exception e) {
            // Can't happen short of a hand-edited row: every definition stored was written by
            // writeDefinition from a validated record. A bug, not a caller error — hence not an AppException.
            log.error("Corrupt scenario definition JSON: {}", e.getMessage());
            throw new IllegalStateException("Corrupt scenario definition: " + e.getMessage(), e);
        }
    }

    public String writeDefinition(ScenarioDefinition definition) {
        try {
            return JSON.writeValueAsString(definition);
        } catch (Exception e) {
            // A record ScenarioService already validated always serialises; failing here is a bug.
            throw new IllegalStateException("Scenario definition could not be serialised: " + e.getMessage(), e);
        }
    }

    public Scenario toScenario(ScenarioEntity entity) {
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
