package uz.murodjon.robotcallv2.tool.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.agent.dialog.HttpToolExecutor;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.event.AiAgentClonedEvent;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;
import uz.murodjon.robotcallv2.tool.application.dto.*;
import uz.murodjon.robotcallv2.tool.application.mapper.ToolMapper;
import uz.murodjon.robotcallv2.tool.application.port.input.ToolUseCase;
import uz.murodjon.robotcallv2.tool.application.port.output.ToolRepository;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolAuth;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolFilter;
import uz.murodjon.robotcallv2.tool.domain.service.ToolValidator;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class ToolService implements ToolUseCase {

    private static final Logger log = LoggerFactory.getLogger(ToolService.class);
    private final ObjectMapper objectMapper;
    private static final TypeReference<Map<String, Object>> RESULT_TYPE = new TypeReference<>() {};

    private final ToolRepository toolRepository;
    private final AiAgentUseCase aiAgentUseCase;
    private final ToolMapper mapper;
    private final HttpToolExecutor httpToolExecutor;

    public ToolService(
            ToolRepository toolRepository,
            AiAgentUseCase aiAgentUseCase,
            ToolMapper mapper,
            HttpToolExecutor httpToolExecutor,
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
        this.httpToolExecutor = httpToolExecutor;
        this.toolRepository = toolRepository;
        this.aiAgentUseCase = aiAgentUseCase;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public ToolRow createTool(long companyId, CreateToolRequest request) {
        requireCallableApiUrl(request.apiUrl());
        requireValidMapping(request.responseMapping());
        Tool tool = mapper.fromCreateRequest(companyId, request);
        ToolValidator.validateBody(tool.apiBody(), tool.bodySchema());
        requireValidAuth(tool.auth());
        Tool saved = toolRepository.save(tool);
        log.info("Created tool {} for company {}", saved.id(), companyId);
        return mapper.toRow(saved);
    }

    /** All or none — an OpenAPI import (UNIVERSAL-OPERATOR-PLAN B.2) half-saved is a guess at what is missing. */
    @Override
    @Transactional
    public List<ToolRow> createTools(long companyId, List<CreateToolRequest> requests) {
        return requests.stream().map(request -> createTool(companyId, request)).toList();
    }

    @Override
    @Transactional
    public ToolRow updateTool(long companyId, long id, UpdateToolRequest request) {
        Tool existing = toolRepository.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.TOOL_NOT_FOUND, id));
        requireCallableApiUrl(request.apiUrl());
        requireValidMapping(request.responseMapping());

        Tool updated = new Tool(
                existing.id(),
                existing.companyId(),
                request.name() != null ? request.name() : existing.name(),
                request.description() != null ? request.description() : existing.description(),
                request.apiUrl() != null ? request.apiUrl() : existing.apiUrl(),
                request.apiMethod() != null ? request.apiMethod() : existing.apiMethod(),
                request.apiHeaders() != null ? request.apiHeaders() : existing.apiHeaders(),
                request.apiBody() != null ? request.apiBody() : existing.apiBody(),
                request.apiQueryParams() != null ? request.apiQueryParams() : existing.apiQueryParams(),
                request.apiPathParams() != null ? request.apiPathParams() : existing.apiPathParams(),
                request.responseTimeoutSecs() != null ? request.responseTimeoutSecs() : existing.responseTimeoutSecs(),
                request.dynamicVariables() != null ? request.dynamicVariables() : existing.dynamicVariables(),
                request.disableInterruptions() != null ? request.disableInterruptions() : existing.disableInterruptions(),
                request.forcePreToolSpeech() != null ? request.forcePreToolSpeech() : existing.forcePreToolSpeech(),
                request.preToolSpeech() != null ? request.preToolSpeech() : existing.preToolSpeech(),
                existing.createdAt(),
                Instant.now(),
                request.responseMapping() != null ? request.responseMapping() : existing.responseMapping(),
                request.retryAttempts() != null ? request.retryAttempts() : existing.retryAttempts(),
                request.maxCallsPerCall() != null ? request.maxCallsPerCall() : existing.maxCallsPerCall(),
                request.bodySchema() != null ? request.bodySchema() : existing.bodySchema(),
                request.effect() != null ? request.effect() : existing.effect(),
                request.requiresConfirmation() != null ? request.requiresConfirmation() : existing.requiresConfirmation(),
                request.readbackTemplate() != null ? request.readbackTemplate() : existing.readbackTemplate(),
                request.auth() == null ? existing.auth() : request.auth().type() == null ? null : request.auth()
        );
        ToolValidator.validateBody(updated.apiBody(), updated.bodySchema());
        requireValidAuth(updated.auth());

        Tool saved = toolRepository.save(updated);
        log.info("Updated tool {} for company {}", id, companyId);
        return mapper.toRow(saved);
    }

    /**
     * Rejects a tool aimed at this network while its author is still looking at the form,
     * rather than only at the moment the model calls it.
     *
     * <p>An address holding a placeholder — a {@code {{secrets.HOST}}} or a {@code {id}}
     * path segment — is not a URL yet and is left alone; what it resolves to is screened
     * by {@code HttpToolExecutor} on every call, which is where the real defence is.
     */
    private static void requireCallableApiUrl(String apiUrl) {
        if (apiUrl == null || apiUrl.isBlank() || apiUrl.indexOf('{') >= 0) {
            return;
        }
        if (PublicUrlGuard.parsePublic(apiUrl) == null) {
            throw new ValidationException(ErrorCode.TOOL_URL_INVALID, apiUrl);
        }
    }

    /** The token endpoint is held to the same rule as the tool's own address. */
    private static void requireValidAuth(ToolAuth auth) {
        ToolValidator.validateAuth(auth);
        if (auth != null) {
            requireCallableApiUrl(auth.tokenUrl());
        }
    }

    /** Every mapped value must be a JSONPath into the answer: {@code $}, {@code $.data.status}. */
    private static void requireValidMapping(Map<String, String> mapping) {
        if (mapping == null) {
            return;
        }
        mapping.forEach((name, path) -> {
            if (name == null || name.isBlank() || path == null || !path.trim().startsWith("$")) {
                throw new ValidationException(ErrorCode.TOOL_RESPONSE_MAPPING_INVALID, name, path);
            }
        });
    }

    /**
     * No transaction around it on purpose: the request goes out over the network and may
     * take the tool's whole timeout.
     */
    @Override
    public ToolTestResponse testTool(long companyId, long id, TestToolRequest request) {
        Tool tool = toolRepository.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.TOOL_NOT_FOUND, id));
        log.info("Test run of tool {} for company {}", id, companyId);
        String result = httpToolExecutor.executeForTest(tool,
                request != null ? request.variables() : null, request != null ? request.args() : null);
        try {
            return new ToolTestResponse(objectMapper.readValue(result, RESULT_TYPE));
        } catch (Exception e) {
            return new ToolTestResponse(Map.of("raw", result));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ToolRow findTool(long companyId, long id) {
        Tool tool = toolRepository.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.TOOL_NOT_FOUND, id));
        return mapper.toRow(tool);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ToolRow> findTools(long companyId) {
        return toolRepository.findByCompanyId(companyId).stream()
                .map(mapper::toRow)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageableData<ToolRow> filterTools(long companyId, ToolFilter filter) {
        ToolFilter sortable = new ToolFilter(filter.search(), filter.page(), filter.size(),
                ToolValidator.validateSortFields(filter.orders()));
        PageableData<Tool> page = toolRepository.search(companyId, sortable);
        List<ToolRow> rows = page.data().stream().map(mapper::toRow).toList();
        return PageableData.of(rows, page.currentPage(), filter.size(), page.totalElements());
    }

    @Override
    @Transactional
    public void deleteTool(long companyId, long id) {
        toolRepository.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.TOOL_NOT_FOUND, id));

        long inUseCount = toolRepository.countAgentsUsingTool(companyId, id);
        if (inUseCount > 0) {
            throw new ConflictException(ErrorCode.TOOL_IN_USE, id, inUseCount);
        }

        toolRepository.deleteByCompanyIdAndId(companyId, id);
        log.info("Deleted tool {} for company {}", id, companyId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ToolRow> findAgentTools(long companyId, long agentId) {
        requireAgent(companyId, agentId);
        return toolRepository.findByCompanyIdAndAgentId(companyId, agentId).stream()
                .map(mapper::toRow)
                .toList();
    }

    @Override
    @Transactional
    public void attachTool(long companyId, long agentId, long toolId) {
        requireAgent(companyId, agentId);
        toolRepository.findByCompanyIdAndId(companyId, toolId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.TOOL_NOT_FOUND, toolId));

        toolRepository.attachToAgent(companyId, agentId, toolId);
        log.info("Attached tool {} to agent {}", toolId, agentId);
    }

    @Override
    @Transactional
    public void detachTool(long companyId, long agentId, long toolId) {
        requireAgent(companyId, agentId);
        toolRepository.detachFromAgent(companyId, agentId, toolId);
        log.info("Detached tool {} from agent {}", toolId, agentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Tool> findAgentToolDefinitions(long companyId, long agentId) {
        return toolRepository.findByCompanyIdAndAgentId(companyId, agentId);
    }

    /**
     * A cloned agent gets its source's tool links, inside the clone's own transaction —
     * the agent feature announces the clone rather than calling this feature back.
     */
    @EventListener
    public void copyToolLinks(AiAgentClonedEvent event) {
        List<Tool> tools = toolRepository.findByCompanyIdAndAgentId(event.companyId(), event.sourceAgentId());
        for (Tool tool : tools) {
            toolRepository.attachToAgent(event.companyId(), event.copyAgentId(), tool.id());
        }
        log.info("Copied {} tool link(s) from agent {} to its clone {}",
                tools.size(), event.sourceAgentId(), event.copyAgentId());
    }

    /** Refuses an agent that is not this company's, so a tool cannot be bound across tenants. */
    private void requireAgent(long companyId, long agentId) {
        aiAgentUseCase.requireAgent(companyId, agentId);
    }
}
