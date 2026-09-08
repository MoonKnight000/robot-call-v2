package uz.murodjon.robotcallv2.tool.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;
import uz.murodjon.robotcallv2.tool.application.dto.CreateToolRequest;
import uz.murodjon.robotcallv2.tool.application.dto.ToolRow;
import uz.murodjon.robotcallv2.tool.application.dto.UpdateToolRequest;
import uz.murodjon.robotcallv2.tool.application.mapper.ToolMapper;
import uz.murodjon.robotcallv2.tool.application.port.input.ToolUseCase;
import uz.murodjon.robotcallv2.tool.application.port.output.ToolRepository;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolFilter;

import java.time.Instant;
import java.util.List;

@Service
public class ToolService implements ToolUseCase {

    private static final Logger log = LoggerFactory.getLogger(ToolService.class);

    private final ToolRepository toolRepository;
    private final AiAgentUseCase aiAgentUseCase;
    private final ToolMapper mapper;

    public ToolService(
            ToolRepository toolRepository,
            AiAgentUseCase aiAgentUseCase,
            ToolMapper mapper
    ) {
        this.toolRepository = toolRepository;
        this.aiAgentUseCase = aiAgentUseCase;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public ToolRow createTool(long companyId, CreateToolRequest request) {
        requireCallableApiUrl(request.apiUrl());
        Tool saved = toolRepository.save(mapper.fromCreateRequest(companyId, request));
        log.info("Created tool {} for company {}", saved.id(), companyId);
        return mapper.toRow(saved);
    }

    @Override
    @Transactional
    public ToolRow updateTool(long companyId, long id, UpdateToolRequest request) {
        Tool existing = toolRepository.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.TOOL_NOT_FOUND, id));
        requireCallableApiUrl(request.apiUrl());

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
                Instant.now()
        );

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
        PageableData<Tool> page = toolRepository.search(companyId, filter);
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

    /** Refuses an agent that is not this company's, so a tool cannot be bound across tenants. */
    private void requireAgent(long companyId, long agentId) {
        aiAgentUseCase.requireAgent(companyId, agentId);
    }
}
