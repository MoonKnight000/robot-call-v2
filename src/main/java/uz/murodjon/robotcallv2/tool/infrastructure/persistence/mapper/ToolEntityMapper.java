package uz.murodjon.robotcallv2.tool.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolAuth;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolHttpConfig;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolKeyValuePair;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolParamConfig;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolSpeech;
import uz.murodjon.robotcallv2.tool.infrastructure.persistence.entity.ToolEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class ToolEntityMapper {

    private static final Logger log = LoggerFactory.getLogger(ToolEntityMapper.class);

    private static final TypeReference<List<ToolKeyValuePair>> KV_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<ToolParamConfig>> PARAM_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> MAPPING_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> SCHEMA_TYPE = new TypeReference<>() {};
    private static final TypeReference<ToolAuth> AUTH_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public ToolEntityMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Tool toTool(ToolEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Tool(
                entity.getId(),
                entity.getCompanyId(),
                entity.getName(),
                entity.getDescription(),
                new ToolHttpConfig(
                        entity.getApiUrl(),
                        entity.getApiMethod(),
                        read(entity.getApiHeaders(), KV_LIST_TYPE, List.of()),
                        read(entity.getApiBody(), PARAM_LIST_TYPE, List.of()),
                        read(entity.getApiQueryParams(), PARAM_LIST_TYPE, List.of()),
                        read(entity.getApiPathParams(), PARAM_LIST_TYPE, List.of()),
                        read(entity.getBodySchema(), SCHEMA_TYPE, Map.of()),
                        entity.getResponseTimeoutSecs(),
                        entity.getRetryAttempts(),
                        read(entity.getAuth(), AUTH_TYPE, null),
                        entity.isWaitForResult(),
                        entity.getStatusVariable()),
                read(entity.getDynamicVariables(), KV_LIST_TYPE, List.of()),
                new ToolSpeech(entity.isDisableInterruptions(), entity.isForcePreToolSpeech(),
                        entity.getPreToolSpeech()),
                read(entity.getResponseMapping(), MAPPING_TYPE, Map.of()),
                entity.getMaxCallsPerCall(),
                entity.getEffect(),
                entity.isRequiresConfirmation(),
                entity.getReadbackTemplate(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
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
        ToolHttpConfig http = domain.http();
        entity.setApiUrl(http.url());
        entity.setApiMethod(http.method());
        entity.setApiHeaders(write(http.headers()));
        entity.setApiBody(write(http.body()));
        entity.setApiQueryParams(write(http.queryParams()));
        entity.setApiPathParams(write(http.pathParams()));
        entity.setResponseTimeoutSecs(http.responseTimeoutSecs());
        entity.setDynamicVariables(write(domain.dynamicVariables()));
        entity.setDisableInterruptions(domain.speech().disableInterruptions());
        entity.setForcePreToolSpeech(domain.speech().forcePreToolSpeech());
        entity.setPreToolSpeech(domain.speech().preToolSpeech());
        entity.setResponseMapping(domain.responseMapping().isEmpty() ? null : write(domain.responseMapping()));
        entity.setRetryAttempts(http.retryAttempts());
        entity.setWaitForResult(http.waitForResult());
        entity.setStatusVariable(http.statusVariable());
        entity.setMaxCallsPerCall(domain.maxCallsPerCall());
        entity.setBodySchema(http.bodySchema().isEmpty() ? null : write(http.bodySchema()));
        entity.setEffect(domain.effect());
        entity.setRequiresConfirmation(domain.requiresConfirmation());
        entity.setReadbackTemplate(domain.readbackTemplate());
        entity.setAuth(http.auth() == null ? null : write(http.auth()));
        entity.setCreatedAt(domain.createdAt() != null ? domain.createdAt() : Instant.now());
        entity.setUpdatedAt(domain.updatedAt() != null ? domain.updatedAt() : Instant.now());
        return entity;
    }

    private <T> T read(String json, TypeReference<T> typeRef, T defaultValue) {
        if (json == null || json.isBlank()) {
            return defaultValue;
        }
        try {
            return objectMapper.readValue(json, typeRef);
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
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to serialize to JSON: {}", e.getMessage());
            return "[]";
        }
    }
}
