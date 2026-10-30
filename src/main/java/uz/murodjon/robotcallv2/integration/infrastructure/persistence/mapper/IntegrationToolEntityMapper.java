package uz.murodjon.robotcallv2.integration.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.integration.domain.entity.IntegrationTool;
import uz.murodjon.robotcallv2.integration.domain.enums.IntegrationToolKind;
import uz.murodjon.robotcallv2.integration.infrastructure.persistence.entity.IntegrationToolEntity;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;

import java.time.Instant;

@Component
public class IntegrationToolEntityMapper {

    private final ObjectMapper objectMapper;

    public IntegrationToolEntityMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public IntegrationTool toIntegrationTool(IntegrationToolEntity entity) {
        return new IntegrationTool(
                entity.getId(),
                entity.getProvider(),
                entity.getToolKey(),
                entity.getKind(),
                entity.getToolGroup(),
                entity.getLabel(),
                entity.getDescription(),
                entity.getPermission(),
                entity.getScope(),
                entity.isDefaultEnabled(),
                entity.isPlatformEnabled(),
                entity.getSortOrder(),
                readDefinition(entity.getDefinition()),
                entity.getModelDescription());
    }

    /**
     * Copies what the platform admin may edit. The key, kind and provider never change; the
     * grant and the definition belong to an HTTP tool only — a built-in tool's are code, and
     * what the model reads about it is {@code modelDescription}.
     */
    public void applyEdits(IntegrationToolEntity entity, IntegrationTool tool) {
        entity.setLabel(tool.label());
        entity.setDescription(tool.description());
        entity.setToolGroup(tool.group());
        entity.setDefaultEnabled(tool.defaultEnabled());
        entity.setPlatformEnabled(tool.platformEnabled());
        entity.setSortOrder(tool.sortOrder());
        if (entity.getKind() == IntegrationToolKind.HTTP) {
            entity.setPermission(tool.permission());
            entity.setScope(tool.scope());
            entity.setDefinition(writeDefinition(tool.definition()));
        } else {
            entity.setModelDescription(tool.modelDescription());
        }
    }

    public IntegrationToolEntity toNewEntity(IntegrationTool tool) {
        IntegrationToolEntity entity = new IntegrationToolEntity();
        entity.setProvider(tool.provider());
        entity.setToolKey(tool.key());
        entity.setKind(tool.kind());
        entity.setPermission(tool.permission());
        entity.setScope(tool.scope());
        entity.setDefinition(writeDefinition(tool.definition()));
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        applyEdits(entity, tool);
        entity.setUpdatedAt(now);
        return entity;
    }

    private Tool readDefinition(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Tool.class);
        } catch (JsonProcessingException e) {
            // Written by writeDefinition below, so unreadable means the row was edited by hand.
            throw new IllegalStateException("an integration tool's stored definition could not be read", e);
        }
    }

    private String writeDefinition(Tool definition) {
        if (definition == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(definition);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("a tool definition could not be serialised", e);
        }
    }
}
