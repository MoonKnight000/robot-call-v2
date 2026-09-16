package uz.murodjon.robotcallv2.quality.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.aiagent.application.dto.SaveAgentTestCaseRequest;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AgentEvaluationUseCase;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeItemCreateRequest;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeBaseUseCase;
import uz.murodjon.robotcallv2.quality.application.dto.ReviewCallRequest;
import uz.murodjon.robotcallv2.quality.application.port.input.CallReviewUseCase;
import uz.murodjon.robotcallv2.quality.application.port.output.CallQualityRepository;
import uz.murodjon.robotcallv2.quality.domain.entity.CallQuality;
import uz.murodjon.robotcallv2.quality.domain.entity.CallReviewFilter;
import uz.murodjon.robotcallv2.quality.domain.service.CallReviewValidator;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.time.Instant;

/**
 * The review inbox (UNIVERSAL-OPERATOR-PLAN D.2). A person's mark is the one ground truth in the
 * improvement loop — the judge is calibrated against it, and the daily insight job (D.3) reads it.
 */
@Service
public class CallReviewService implements CallReviewUseCase {

    private final CallQualityRepository repository;
    private final KnowledgeBaseUseCase knowledgeBaseUseCase;
    private final AgentEvaluationUseCase agentEvaluationUseCase;
    private final CurrentUser currentUser;
    private final AuditUseCase auditUseCase;

    public CallReviewService(CallQualityRepository repository, KnowledgeBaseUseCase knowledgeBaseUseCase,
                             AgentEvaluationUseCase agentEvaluationUseCase, CurrentUser currentUser,
                             AuditUseCase auditUseCase) {
        this.repository = repository;
        this.knowledgeBaseUseCase = knowledgeBaseUseCase;
        this.agentEvaluationUseCase = agentEvaluationUseCase;
        this.currentUser = currentUser;
        this.auditUseCase = auditUseCase;
    }

    @Override
    public PageableData<CallQuality> filterReviews(long companyId, CallReviewFilter filter) {
        CallReviewValidator.validateRange(filter.from(), filter.to());
        return repository.findPageByCompanyId(companyId, filter);
    }

    @Override
    public CallQuality findReview(long companyId, long callAttemptId) {
        return requireQuality(companyId, callAttemptId);
    }

    @Override
    @Transactional
    public CallQuality recordReview(long companyId, long callAttemptId, ReviewCallRequest request) {
        CallReviewValidator.validateReview(request.status(), request.reason());
        CallQuality quality = requireQuality(companyId, callAttemptId);
        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().trim();
        CallQuality saved = repository.save(quality.withReview(request.status(), reason,
                currentUser.id().orElse(null), Instant.now()));
        auditUseCase.record(companyId, "CALL_REVIEW", "call", String.valueOf(callAttemptId), request.status().name());
        return saved;
    }

    @Override
    @Transactional
    public CallQuality createKnowledgeItem(long companyId, long callAttemptId, KnowledgeItemCreateRequest request) {
        CallQuality quality = requireQuality(companyId, callAttemptId);
        long agentId = requireAgentId(quality);
        long itemId = knowledgeBaseUseCase.create(companyId, new KnowledgeItemCreateRequest(agentId, request.key(),
                request.topic(), request.title(), request.answerUz(), request.answerRu(), request.answerEn(),
                request.keywords())).id();
        return repository.save(quality.withKnowledgeItemId(itemId));
    }

    @Override
    @Transactional
    public CallQuality createTestCase(long companyId, long callAttemptId, SaveAgentTestCaseRequest request) {
        CallQuality quality = requireQuality(companyId, callAttemptId);
        long testCaseId = agentEvaluationUseCase.createTestCase(companyId, requireAgentId(quality), request).id();
        return repository.save(quality.withTestCaseId(testCaseId));
    }

    private CallQuality requireQuality(long companyId, long callAttemptId) {
        CallQuality quality = repository.findByCompanyIdAndCallAttemptId(companyId, callAttemptId);
        if (quality == null) {
            throw new NotFoundException(ErrorCode.CALL_QUALITY_NOT_FOUND, callAttemptId);
        }
        return quality;
    }

    /** The agent the answer or the test case goes to; a call whose agent was deleted has none. */
    private static long requireAgentId(CallQuality quality) {
        if (quality.aiAgentId() == null) {
            throw new ConflictException(ErrorCode.CALL_REVIEW_NO_AGENT, quality.callAttemptId());
        }
        return quality.aiAgentId();
    }
}
