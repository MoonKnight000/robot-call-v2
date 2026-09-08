package uz.murodjon.robotcallv2.tool.application.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.tool.application.dto.CreateToolRequest;
import uz.murodjon.robotcallv2.tool.application.dto.ToolRow;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolKeyValuePair;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolParamConfig;
import uz.murodjon.robotcallv2.tool.infrastructure.persistence.entity.ToolEntity;

import java.time.Instant;
import java.util.List;

@Component
public class ToolMapper {

    private static final Logger log = LoggerFactory.getLogger(ToolMapper.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final TypeReference<List<ToolKeyValuePair>> KV_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<ToolParamConfig>> PARAM_LIST_TYPE = new TypeReference<>() {};

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
                entity.getUpdatedAt()
        );
    }

    public ToolEntity toEntity(Tool domain, CompanyEntity company) {
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
        entity.setCreatedAt(domain.createdAt() != null ? domain.createdAt() : Instant.now());
        entity.setUpdatedAt(domain.updatedAt() != null ? domain.updatedAt() : Instant.now());
        return entity;
    }

    public Tool fromCreateRequest(long companyId, CreateToolRequest request) {
        Instant now = Instant.now();
        return new Tool(
                null,
                companyId,
                request.name(),
                request.description(),
                request.apiUrl(),
                request.apiMethod(),
                request.apiHeaders(),
                request.apiBody(),
                request.apiQueryParams(),
                request.apiPathParams(),
                request.responseTimeoutSecs(),
                request.dynamicVariables(),
                request.disableInterruptions() != null && request.disableInterruptions(),
                request.forcePreToolSpeech() == null || request.forcePreToolSpeech(),
                request.preToolSpeech(),
                now,
                now
        );
    }

    public ToolRow toRow(Tool tool) {
        if (tool == null) {
            return null;
        }
        return new ToolRow(
                tool.id(),
                tool.companyId(),
                tool.name(),
                tool.description(),
                tool.apiUrl(),
                tool.apiMethod(),
                tool.apiHeaders(),
                tool.apiBody(),
                tool.apiQueryParams(),
                tool.apiPathParams(),
                tool.responseTimeoutSecs(),
                tool.dynamicVariables(),
                tool.disableInterruptions(),
                tool.forcePreToolSpeech(),
                tool.preToolSpeech(),
                tool.createdAt(),
                tool.updatedAt()
        );
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
