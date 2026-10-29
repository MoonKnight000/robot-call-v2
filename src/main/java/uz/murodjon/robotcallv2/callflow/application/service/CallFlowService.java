package uz.murodjon.robotcallv2.callflow.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.callflow.application.dto.CallFlowValidationResponse;
import uz.murodjon.robotcallv2.callflow.application.dto.SaveCallFlowRequest;
import uz.murodjon.robotcallv2.callflow.application.port.input.CallFlowUseCase;
import uz.murodjon.robotcallv2.callflow.application.port.output.CallFlowRepository;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlow;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlowFilter;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlowGraph;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlowNode;
import uz.murodjon.robotcallv2.callflow.domain.enums.CallFlowNodeType;
import uz.murodjon.robotcallv2.callflow.domain.service.CallFlowValidator;
import uz.murodjon.robotcallv2.callqueue.application.port.input.CallQueueUseCase;
import uz.murodjon.robotcallv2.schedule.application.port.input.BusinessScheduleUseCase;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.*;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;
import uz.murodjon.robotcallv2.storage.application.dto.DownloadableFile;
import uz.murodjon.robotcallv2.storage.application.port.input.FileStorageUseCase;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;
import uz.murodjon.robotcallv2.voicemail.application.port.input.VoicemailUseCase;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Call flows (docs/INBOUND-PBX-PLAN.md §4): drafts saved as they are edited, published
 * only once the whole graph is valid and everything it points at is the company's own.
 */
@Service
public class CallFlowService implements CallFlowUseCase {

    private static final Logger log = LoggerFactory.getLogger(CallFlowService.class);

    private final CallFlowRepository repository;
    private final BusinessScheduleUseCase businessScheduleUseCase;
    private final CallQueueUseCase callQueueUseCase;
    /** Lazy: an agent names a transfer queue, a queue a waiting agent, and a flow both. */
    private final AiAgentUseCase aiAgentUseCase;
    private final VoicemailUseCase voicemailUseCase;
    private final UserUseCase userUseCase;
    private final FileStorageUseCase fileStorageUseCase;
    private final AuditUseCase auditService;
    private final Clock clock;

    public CallFlowService(CallFlowRepository repository, BusinessScheduleUseCase businessScheduleUseCase,
                           CallQueueUseCase callQueueUseCase, @Lazy AiAgentUseCase aiAgentUseCase,
                           VoicemailUseCase voicemailUseCase, UserUseCase userUseCase,
                           FileStorageUseCase fileStorageUseCase, AuditUseCase auditService, Clock clock) {
        this.repository = repository;
        this.businessScheduleUseCase = businessScheduleUseCase;
        this.callQueueUseCase = callQueueUseCase;
        this.aiAgentUseCase = aiAgentUseCase;
        this.voicemailUseCase = voicemailUseCase;
        this.userUseCase = userUseCase;
        this.fileStorageUseCase = fileStorageUseCase;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Override
    public CallFlow create(long companyId, SaveCallFlowRequest request) {
        long id = repository.create(companyId, request.name().trim(), blankToNull(request.description()), request.graph());
        auditService.record(companyId, "CALL_FLOW_CREATE", "call_flow", String.valueOf(id), request.name());
        return requireFlow(companyId, id);
    }

    @Override
    public CallFlow update(long companyId, long id, SaveCallFlowRequest request) {
        requireFlow(companyId, id);
        repository.updateDraft(companyId, id, request.name().trim(), blankToNull(request.description()), request.graph());
        auditService.record(companyId, "CALL_FLOW_UPDATE", "call_flow", String.valueOf(id), request.name());
        return requireFlow(companyId, id);
    }

    @Override
    public void delete(long companyId, long id) {
        requireFlow(companyId, id);
        repository.delete(companyId, id);
        auditService.record(companyId, "CALL_FLOW_DELETE", "call_flow", String.valueOf(id), null);
    }

    @Override
    public CallFlow requireFlow(long companyId, long id) {
        CallFlow flow = repository.find(companyId, id);
        if (flow == null) {
            throw new NotFoundException(ErrorCode.CALL_FLOW_NOT_FOUND, id);
        }
        return flow;
    }

    @Override
    public CallFlow requirePublishedFlow(long companyId, long id) {
        CallFlow flow = requireFlow(companyId, id);
        if (!flow.published()) {
            throw new ConflictException(ErrorCode.CALL_FLOW_NOT_PUBLISHED, id);
        }
        return flow;
    }

    @Override
    public PageableData<CallFlow> list(long companyId, CallFlowFilter filter) {
        return PageableData.of(repository.findAll(companyId, filter), filter.pageOrDefault(), filter.sizeOrDefault(),
                repository.count(companyId));
    }

    @Override
    public CallFlowValidationResponse validateDraft(long companyId, long id) {
        CallFlow flow = requireFlow(companyId, id);
        List<String> problems = new ArrayList<>(CallFlowValidator.findProblems(flow.draftGraph()));
        if (problems.isEmpty()) {
            problems.addAll(findReferenceProblems(companyId, flow.draftGraph()));
        }
        return new CallFlowValidationResponse(problems.isEmpty(), problems);
    }

    @Override
    public CallFlow publish(long companyId, long id) {
        CallFlow flow = requireFlow(companyId, id);
        CallFlowValidator.validate(flow.draftGraph());
        List<String> problems = findReferenceProblems(companyId, flow.draftGraph());
        if (!problems.isEmpty()) {
            throw ValidationException.withErrors(ErrorCode.CALL_FLOW_INVALID, problems, problems.size());
        }
        repository.publish(companyId, id, flow.draftGraph(), clock.instant());
        auditService.record(companyId, "CALL_FLOW_PUBLISH", "call_flow", String.valueOf(id),
                "version " + (flow.version() + 1));
        return requireFlow(companyId, id);
    }

    @Override
    public CallFlowGraph findPublishedGraph(long companyId, long id) {
        CallFlow flow = repository.find(companyId, id);
        return flow != null ? flow.publishedGraph() : null;
    }

    @Override
    public Map<Long, String> findNamesByIds(long companyId, Collection<Long> ids) {
        return repository.findNamesByIds(companyId, ids);
    }

    /** Everything a node points at must exist and be this company's — a live call does not ask again. */
    private List<String> findReferenceProblems(long companyId, CallFlowGraph graph) {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, CallFlowNode> entry : graph.nodes().entrySet()) {
            String id = entry.getKey();
            CallFlowNode node = entry.getValue();
            check(problems, id, () -> {
                if (node.audioFileId() != null) {
                    requireOwnFile(companyId, node.audioFileId());
                }
                switch (node.type()) {
                    case SCHEDULE -> businessScheduleUseCase.requireSchedule(companyId, node.scheduleId());
                    case QUEUE -> callQueueUseCase.requireQueue(companyId, node.queueId());
                    case AI_AGENT -> aiAgentUseCase.requireAgent(companyId, node.aiAgentId());
                    case VOICEMAIL -> voicemailUseCase.requireBox(companyId, node.voicemailBoxId());
                    case RING_GROUP, EXTENSION -> node.userIds().forEach(userId -> userUseCase.requireUser(companyId, userId));
                    default -> {
                        // nothing outside the graph
                    }
                }
            });
            if (node.type() == CallFlowNodeType.WEBHOOK_ROUTER && PublicUrlGuard.parsePublic(node.url()) == null) {
                problems.add(id + ": url must be a public http(s) address");
            }
        }
        return problems;
    }

    private static void check(List<String> problems, String nodeId, Runnable lookup) {
        try {
            lookup.run();
        } catch (AppException e) {
            problems.add(nodeId + ": " + e.getMessage());
        }
    }

    private void requireOwnFile(long companyId, long fileId) {
        DownloadableFile file = fileStorageUseCase.download(companyId, fileId);
        try (InputStream ignored = file.content()) {
            // only the access check mattered
        } catch (IOException e) {
            log.debug("Closing prompt file {} failed: {}", fileId, e.getMessage());
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
