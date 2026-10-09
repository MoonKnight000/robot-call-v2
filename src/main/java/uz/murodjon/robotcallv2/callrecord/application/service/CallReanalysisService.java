package uz.murodjon.robotcallv2.callrecord.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.summary.SummaryService;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.billing.domain.entity.CallUsage;
import uz.murodjon.robotcallv2.callrecord.application.dto.CallReanalysisPreviewResponse;
import uz.murodjon.robotcallv2.callrecord.application.dto.CreateCallReanalysisRequest;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallReanalysisUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallReanalysisJobRepository;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallResultRepository;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallReanalysisFilter;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallReanalysisJob;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallReanalysisScope;
import uz.murodjon.robotcallv2.callrecord.domain.service.CallReanalysisValidator;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.time.Clock;

/**
 * Queues and reports {@link CallReanalysisJob}s; {@link CallReanalysisWorker} runs them.
 *
 * <p>The agent's analysis settings are read from its draft — what its editors see — so a field
 * can be tried on past calls before the agent is published with it.
 */
@Service
public class CallReanalysisService implements CallReanalysisUseCase {

    /** Roughly how many characters of Uzbek or Russian transcript make one token. */
    private static final int CHARS_PER_TOKEN = 3;
    /** The analysis instructions sent with every call, in tokens. */
    private static final int PROMPT_OVERHEAD_TOKENS = 1500;
    /** A typical analysis answer, in tokens. */
    private static final int ANSWER_TOKENS = 500;

    private final CallReanalysisJobRepository repository;
    private final CallResultRepository callResultRepository;
    private final AiAgentUseCase aiAgentUseCase;
    private final CallBillingUseCase callBillingUseCase;
    private final SummaryService summaryService;
    private final Clock clock;

    public CallReanalysisService(CallReanalysisJobRepository repository, CallResultRepository callResultRepository,
                                 AiAgentUseCase aiAgentUseCase, CallBillingUseCase callBillingUseCase,
                                 SummaryService summaryService, Clock clock) {
        this.repository = repository;
        this.callResultRepository = callResultRepository;
        this.aiAgentUseCase = aiAgentUseCase;
        this.callBillingUseCase = callBillingUseCase;
        this.summaryService = summaryService;
        this.clock = clock;
    }

    @Override
    public CallReanalysisPreviewResponse previewReanalysis(long companyId, CreateCallReanalysisRequest request) {
        CallReanalysisScope scope = findScope(companyId, request);
        long promptTokens = scope.transcriptChars() / CHARS_PER_TOKEN + scope.callCount() * PROMPT_OVERHEAD_TOKENS;
        long completionTokens = scope.callCount() * ANSWER_TOKENS;
        long costUzs = callBillingUseCase.findUsageCostUzs(new CallUsage(0, promptTokens, completionTokens, 0, 0));
        return new CallReanalysisPreviewResponse(scope.callCount(), CallReanalysisValidator.MAX_CALLS,
                promptTokens, completionTokens, costUzs);
    }

    @Override
    public CallReanalysisJob createReanalysis(long companyId, CreateCallReanalysisRequest request) {
        CallReanalysisScope scope = findScope(companyId, request);
        CallReanalysisValidator.validateCallCount(scope.callCount());
        if (!summaryService.isAvailable()) {
            throw new ConflictException(ErrorCode.CALL_REANALYSIS_UNAVAILABLE);
        }
        if (repository.existsActiveByCompanyId(companyId)) {
            throw new ConflictException(ErrorCode.CALL_REANALYSIS_ALREADY_ACTIVE);
        }
        if (!callBillingUseCase.hasBalanceForCall(companyId)) {
            throw new ConflictException(ErrorCode.CALL_REANALYSIS_NO_BALANCE);
        }
        return repository.create(CallReanalysisJob.queued(companyId, request.aiAgentId(), request.from(),
                request.to(), (int) scope.callCount()));
    }

    @Override
    public CallReanalysisJob findReanalysis(long companyId, long id) {
        CallReanalysisJob job = repository.findByCompanyIdAndId(companyId, id);
        if (job == null) {
            throw new NotFoundException(ErrorCode.CALL_REANALYSIS_NOT_FOUND, id);
        }
        return job;
    }

    @Override
    public PageableData<CallReanalysisJob> filterReanalyses(long companyId, CallReanalysisFilter filter) {
        return repository.findPageByCompanyId(companyId, filter);
    }

    @Override
    public CallReanalysisJob cancelReanalysis(long companyId, long id) {
        CallReanalysisJob job = findReanalysis(companyId, id);
        if (!repository.cancel(companyId, id, clock.instant())) {
            throw new ConflictException(ErrorCode.CALL_REANALYSIS_NOT_ACTIVE, id, job.status());
        }
        return findReanalysis(companyId, id);
    }

    /** The calls the request covers, once the range and the agent have been checked. */
    private CallReanalysisScope findScope(long companyId, CreateCallReanalysisRequest request) {
        CallReanalysisValidator.validateRange(request.from(), request.to());
        AiAgent agent = aiAgentUseCase.requireAgent(companyId, request.aiAgentId());
        if (agent.dataNeeded().isEmpty() && agent.dataEvaluation().isEmpty()) {
            throw new ValidationException(ErrorCode.CALL_REANALYSIS_NOTHING_TO_ANALYSE, request.aiAgentId());
        }
        return callResultRepository.findReanalysisScope(companyId, request.aiAgentId(), request.from(), request.to());
    }
}
