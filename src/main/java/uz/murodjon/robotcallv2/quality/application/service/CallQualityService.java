package uz.murodjon.robotcallv2.quality.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.summary.CallQualityJudge;
import uz.murodjon.robotcallv2.agent.summary.QualityVerdict;
import uz.murodjon.robotcallv2.quality.application.port.input.CallQualityUseCase;
import uz.murodjon.robotcallv2.quality.application.port.output.CallQualityRepository;
import uz.murodjon.robotcallv2.quality.domain.entity.CallQuality;
import uz.murodjon.robotcallv2.quality.domain.entity.FinishedCall;
import uz.murodjon.robotcallv2.quality.domain.entity.ScoredAgent;
import uz.murodjon.robotcallv2.quality.domain.enums.CallReviewStatus;
import uz.murodjon.robotcallv2.quality.domain.service.RepairCounter;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.tool.application.port.input.ToolExecutionUseCase;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

/**
 * Writes each finished AI call's quality row. Separate from {@link CallReviewService}: this one is
 * reached from the post-call pipeline and must stay free of what the review screen needs.
 */
@Service
public class CallQualityService implements CallQualityUseCase {

    private final CallQualityRepository repository;
    private final CallQualityJudge qualityJudge;
    private final ToolExecutionUseCase toolExecutionUseCase;

    public CallQualityService(CallQualityRepository repository, CallQualityJudge qualityJudge,
                              ToolExecutionUseCase toolExecutionUseCase) {
        this.repository = repository;
        this.qualityJudge = qualityJudge;
        this.toolExecutionUseCase = toolExecutionUseCase;
    }

    /** Not transactional: the judge is an LLM call of several seconds, and it runs before any write. */
    @Override
    public void recordCallQuality(long companyId, FinishedCall call, ScenarioDefinition scenario) {
        QualityVerdict verdict = qualityJudge.judge(call.transcript(), scenario, call.disposition());
        boolean needsReview = verdict != null && verdict.needsReview();
        repository.save(new CallQuality(
                null,
                companyId,
                call.callAttemptId(),
                call.aiAgentId(),
                call.agentVersion(),
                call.disposition(),
                verdict != null ? verdict.score() : null,
                flagOf(verdict, QualityVerdict::guardrailViolation),
                flagOf(verdict, QualityVerdict::languageMismatch),
                flagOf(verdict, QualityVerdict::talkedOverClient),
                flagOf(verdict, QualityVerdict::outcomeCorrect),
                flagOf(verdict, QualityVerdict::goalReached),
                flagOf(verdict, QualityVerdict::unansweredQuestion),
                flagOf(verdict, QualityVerdict::toolMisuse),
                verdict != null && !call.zeroPiiRetention() ? verdict.note() : null,
                needsReview,
                call.turnCount(),
                RepairCounter.countRepairs(call.transcript()),
                call.bargeInCount(),
                call.noInputPromptCount(),
                call.lowConfidenceCount(),
                toolExecutionUseCase.countFailedExecutions(companyId, call.callAttemptId()),
                false,
                needsReview ? CallReviewStatus.PENDING : null,
                null, null, null, null, null, null));
        if (!call.earlierCallIds().isEmpty()) {
            repository.markRepeatCalls(companyId, call.earlierCallIds());
        }
    }

    @Override
    public List<ScoredAgent> findAgentsScoredSince(Instant since) {
        return repository.findAgentsScoredSince(since);
    }

    @Override
    public List<CallQuality> findByAgentSince(long companyId, long agentId, Instant since) {
        return repository.findByAgentSince(companyId, agentId, since);
    }

    private static Boolean flagOf(QualityVerdict verdict, Function<QualityVerdict, Boolean> flag) {
        return verdict == null ? null : flag.apply(verdict);
    }
}
