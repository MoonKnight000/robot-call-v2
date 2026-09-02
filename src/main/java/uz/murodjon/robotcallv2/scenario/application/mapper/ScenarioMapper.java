package uz.murodjon.robotcallv2.scenario.application.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.infrastructure.persistence.entity.ScenarioEntity;

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

    public String writeDefinition(ScenarioDefinition def) {
        try {
            return JSON.writeValueAsString(def);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not serialize scenario definition: " + e.getMessage(), e);
        }
    }

    public Scenario entityToDomain(ScenarioEntity e) {
        if (e == null) {
            return null;
        }
        return new Scenario(
                e.getId(),
                e.getScenarioKey(),
                e.getVersion(),
                e.getName(),
                e.getDescription(),
                e.isBuiltin(),
                e.isActive(),
                readDefinition(e.getDefinition()),
                e.getCreatedAt(),
                e.getCreatedBy()
        );
    }
}
