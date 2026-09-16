package uz.murodjon.robotcallv2.insight.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.aiagent.application.dto.SaveAgentTestCaseRequest;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AgentEvaluationUseCase;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentEvalRun;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.insight.application.dto.RejectInsightRequest;
import uz.murodjon.robotcallv2.insight.application.port.input.AgentInsightUseCase;
import uz.murodjon.robotcallv2.insight.application.port.output.AgentInsightRepository;
import uz.murodjon.robotcallv2.insight.domain.entity.AgentInsight;
import uz.murodjon.robotcallv2.insight.domain.entity.AgentInsightFilter;
import uz.murodjon.robotcallv2.insight.domain.enums.InsightStatus;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * The improvement inbox (UNIVERSAL-OPERATOR-PLAN D.3–D.4): a person decides, and accepting
 * carries the decision out.
 *
 * <p>How far "carrying it out" goes depends on the finding. A prompt change is the one that
 * can make calls worse, so it is the one held behind a gate: the sentence goes into the draft
 * and the agent's own test cases are played against it at once ({@link InsightEvalGate}
 * settles what happens next). A test case is created outright — one that is never run changes
 * nothing on its own. The rest name a problem whose fix is a person's judgement (what the
 * knowledge base should say, how a tool parameter should be described), and accepting those
 * means "yes, this is real" and nothing more.
 *
 * <p>Nothing here publishes. The draft is as far as the loop reaches; the button that puts a
 * version on calls stays a person's (D.4).
 */
@Service
public class AgentInsightService implements AgentInsightUseCase {

    private static final Logger log = LoggerFactory.getLogger(AgentInsightService.class);

    private final AgentInsightRepository repository;
    private final AiAgentUseCase aiAgentUseCase;
    private final AgentEvaluationUseCase agentEvaluationUseCase;
    private final CurrentUser currentUser;
    private final AuditUseCase auditUseCase;
    private final Clock clock;

    public AgentInsightService(AgentInsightRepository repository,
                               AiAgentUseCase aiAgentUseCase,
                               AgentEvaluationUseCase agentEvaluationUseCase,
                               CurrentUser currentUser,
                               AuditUseCase auditUseCase,
                               Clock clock) {
        this.repository = repository;
        this.aiAgentUseCase = aiAgentUseCase;
        this.agentEvaluationUseCase = agentEvaluationUseCase;
        this.currentUser = currentUser;
        this.auditUseCase = auditUseCase;
        this.clock = clock;
    }

    @Override
    public PageableData<AgentInsight> filterInsights(long companyId, AgentInsightFilter filter) {
        return repository.findPageByCompanyId(companyId, filter);
    }

    @Override
    public AgentInsight findInsight(long companyId, long insightId) {
        return requireInsight(companyId, insightId);
    }

    /**
     * Deliberately not one transaction. The row saying a change is being evaluated has to be
     * committed before that evaluation can finish, or the run would come back to an inbox
     * that does not yet know it is waiting for it. An evaluation is several model calls long
     * and the write that follows it here is one row, so the order below is the guarantee.
     */
    @Override
    public AgentInsight acceptInsight(long companyId, long insightId) {
        AgentInsight insight = requireOpen(requireInsight(companyId, insightId));
        Long userId = currentUser.id().orElse(null);
        Instant now = clock.instant();
        auditUseCase.record(companyId, "AGENT_INSIGHT_ACCEPT", "agent_insight", String.valueOf(insightId),
                insight.type().name());
        return switch (insight.type()) {
            case PROMPT_FIX -> startGatedPromptChange(companyId, insight, userId, now);
            case NEW_TEST_CASE -> createTestCase(companyId, insight, userId, now);
            default -> repository.save(insight.accepted(
                    "Belgilandi — bu o'zgarishni odam kiritadi", userId, now));
        };
    }

    @Override
    public AgentInsight rejectInsight(long companyId, long insightId, RejectInsightRequest request) {
        AgentInsight insight = requireOpen(requireInsight(companyId, insightId));
        String reason = request == null || request.reason() == null || request.reason().isBlank()
                ? null : request.reason().trim();
        auditUseCase.record(companyId, "AGENT_INSIGHT_REJECT", "agent_insight", String.valueOf(insightId),
                insight.type().name());
        return repository.save(insight.rejected(reason, currentUser.id().orElse(null), clock.instant()));
    }

    /**
     * The guardrail goes into the draft and the agent is evaluated against it at once. If the
     * run cannot even be started the change is taken back out, so a caller who gets an error
     * finds the draft exactly as it was.
     */
    private AgentInsight startGatedPromptChange(long companyId, AgentInsight insight, Long userId, Instant now) {
        String rule = requireSuggestion(insight);
        if (agentEvaluationUseCase.findTestCases(companyId, insight.aiAgentId()).isEmpty()) {
            // Without test cases there is no gate, and a prompt change that nothing checks is
            // what this whole step exists to prevent.
            throw new ConflictException(ErrorCode.AGENT_EVAL_NO_TEST_CASES, insight.aiAgentId());
        }
        aiAgentUseCase.changeScenarioGuardrail(companyId, insight.aiAgentId(), rule, true);
        AgentEvalRun run;
        try {
            run = agentEvaluationUseCase.evaluate(companyId, insight.aiAgentId(), null);
        } catch (RuntimeException e) {
            aiAgentUseCase.changeScenarioGuardrail(companyId, insight.aiAgentId(), rule, false);
            throw e;
        }
        log.info("[company {}] Insight {} is in agent {}'s draft; evaluation {} decides whether it stays",
                companyId, insight.id(), insight.aiAgentId(), run.id());
        return repository.save(insight.evaluating(run.id(), userId, now));
    }

    private AgentInsight createTestCase(long companyId, AgentInsight insight, Long userId, Instant now) {
        String persona = requireSuggestion(insight);
        long caseId = agentEvaluationUseCase.createTestCase(companyId, insight.aiAgentId(),
                new SaveAgentTestCaseRequest(insight.title(), persona, null, null, null, null,
                        List.of(), List.of(), null)).id();
        return repository.save(insight.accepted("Test holati " + caseId + " yaratildi", userId, now));
    }

    private static String requireSuggestion(AgentInsight insight) {
        if (insight.suggestion() == null || insight.suggestion().isBlank()) {
            throw new ConflictException(ErrorCode.AGENT_INSIGHT_NOTHING_TO_APPLY, insight.id());
        }
        return insight.suggestion().trim();
    }

    private AgentInsight requireInsight(long companyId, long insightId) {
        AgentInsight insight = repository.findByCompanyIdAndId(companyId, insightId);
        if (insight == null) {
            throw new NotFoundException(ErrorCode.AGENT_INSIGHT_NOT_FOUND, insightId);
        }
        return insight;
    }

    private static AgentInsight requireOpen(AgentInsight insight) {
        if (insight.status() != InsightStatus.PROPOSED) {
            throw new ConflictException(ErrorCode.AGENT_INSIGHT_ALREADY_DECIDED, insight.id(), insight.status());
        }
        return insight;
    }
}
