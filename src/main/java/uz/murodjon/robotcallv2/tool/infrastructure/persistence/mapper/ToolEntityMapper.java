package uz.murodjon.robotcallv2.tool.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolAuth;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolKeyValuePair;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolParamConfig;
import uz.murodjon.robotcallv2.tool.infrastructure.persistence.entity.ToolEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class ToolEntityMapper {

    private static final Logger log = LoggerFactory.getLogger(ToolEntityMapper.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final TypeReference<List<ToolKeyValuePair>> KV_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<ToolParamConfig>> PARAM_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> MAPPING_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> SCHEMA_TYPE = new TypeReference<>() {};
    private static final TypeReference<ToolAuth> AUTH_TYPE = new TypeReference<>() {};

    public Tool toTool(ToolEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Tool(
                entity.getId(),
                entity.getCompanyId(),
                entity.getName(),
                entity.getDescription(),
                entity.getApiUrl(),
                entity.getApiMethod(),
                read(entity.getApiHeaders(), KV_LIST_TYPE, List.of()),
                read(entity.getApiBody(), PARAM_LIST_TYPE, List.of()),
                read(entity.getApiQueryParams(), PARAM_LIST_TYPE, List.of()),
                read(entity.getApiPathParams(), PARAM_LIST_TYPE, List.of()),
                entity.getResponseTimeoutSecs(),
                read(entity.getDynamicVariables(), KV_LIST_TYPE, List.of()),
                entity.isDisableInterruptions(),
                entity.isForcePreToolSpeech(),
                entity.getPreToolSpeech(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                read(entity.getResponseMapping(), MAPPING_TYPE, Map.of()),
                entity.getRetryAttempts(),
                entity.getMaxCallsPerCall(),
                read(entity.getBodySchema(), SCHEMA_TYPE, Map.of()),
                entity.getEffect(),
                entity.isRequiresConfirmation(),
                entity.getReadbackTemplate(),
                read(entity.getAuth(), AUTH_TYPE, null)
        );
    }

    public ToolEntity toToolEntity(Tool domain, CompanyEntity company) {
        if (domain == null) {
            return null;
        }
        ToolEntity entity = new ToolEntity();
        entity.setId(domain.id());
        entity.setCompany(company);
        entity.setName(domain.name());
        entity.setDescription(domain.description());
        entity.setApiUrl(domain.apiUrl());
        entity.setApiMethod(domain.apiMethod());
        entity.setApiHeaders(write(domain.apiHeaders()));
        entity.setApiBody(write(domain.apiBody()));
        entity.setApiQueryParams(write(domain.apiQueryParams()));
        entity.setApiPathParams(write(domain.apiPathParams()));
        entity.setResponseTimeoutSecs(domain.responseTimeoutSecs());
        entity.setDynamicVariables(write(domain.dynamicVariables()));
        entity.setDisableInterruptions(domain.disableInterruptions());
        entity.setForcePreToolSpeech(domain.forcePreToolSpeech());
        entity.setPreToolSpeech(domain.preToolSpeech());
        entity.setResponseMapping(domain.responseMapping().isEmpty() ? null : write(domain.responseMapping()));
        entity.setRetryAttempts(domain.retryAttempts());
        entity.setMaxCallsPerCall(domain.maxCallsPerCall());
        entity.setBodySchema(domain.bodySchema().isEmpty() ? null : write(domain.bodySchema()));
        entity.setEffect(domain.effect());
        entity.setRequiresConfirmation(domain.requiresConfirmation());
        entity.setReadbackTemplate(domain.readbackTemplate());
        entity.setAuth(domain.auth() == null ? null : write(domain.auth()));
        entity.setCreatedAt(domain.createdAt() != null ? domain.createdAt() : Instant.now());
        entity.setUpdatedAt(domain.updatedAt() != null ? domain.updatedAt() : Instant.now());
        return entity;
    }

    private <T> T read(String json, TypeReference<T> typeRef, T defaultValue) {
        if (json == null || json.isBlank()) {
            return defaultValue;
        }
        try {
            return JSON.readValue(json, typeRef);
        } catch (Exception e) {
            log.warn("Failed to deserialize JSON: {}", e.getMessage());
            return defaultValue;
        }
    }

    private String write(Object value) {
        if (value == null) {
            return "[]";
        }
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to serialize to JSON: {}", e.getMessage());
            return "[]";
        }
    }
}
