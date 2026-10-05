package uz.murodjon.robotcallv2.aiagent.application.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.dialog.AgentSimulator;
import uz.murodjon.robotcallv2.agent.dialog.SimulatedCall;
import uz.murodjon.robotcallv2.agent.dialog.SimulationPersona;
import uz.murodjon.robotcallv2.agent.dialog.SimulationProperties;
import uz.murodjon.robotcallv2.agent.summary.CallQualityJudge;
import uz.murodjon.robotcallv2.agent.summary.QualityVerdict;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentEvalRunDetail;
import uz.murodjon.robotcallv2.aiagent.application.dto.SaveAgentTestCaseRequest;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AgentEvaluationUseCase;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AgentEvalRepository;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AgentTestCaseRepository;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentEvalResult;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentEvalRun;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentTestCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentEvalRunStatus;
import uz.murodjon.robotcallv2.aiagent.domain.event.AgentEvalFinishedEvent;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Plays an agent's test cases against it and keeps what happened (NEXT-PLAN A.2).
 *
 * <p>Each case is one {@link AgentSimulator} call: the agent — its draft, or the published
 * version asked for — against a second model playing the case's persona. A case passes
 * when the call ends where it should ({@code expectDisposition}, {@code expectStage}), the
 * agent said every {@code mustSay} phrase and none of the {@code mustNotSay} ones, the fact
 * guard caught nothing, and the quality judge found no guardrail violation.
 *
 * <p>A run goes on a virtual thread of its own and is read back by polling; one agent has
 * at most one run going. A run left RUNNING by a restart stops counting as running after
 * {@link #STALE_RUN}, so it never blocks the next one.
 */
@Service
public class AgentEvaluationService implements AgentEvaluationUseCase {

    private static final Logger log = LoggerFactory.getLogger(AgentEvaluationService.class);

    private static final Duration STALE_RUN = Duration.ofMinutes(30);
    private static final int RUNS_LISTED = 20;
    private static final int MAX_ERROR_CHARS = 500;

    private final AgentTestCaseRepository testCaseRepository;
    private final AgentEvalRepository evalRepository;
    private final AiAgentUseCase aiAgentUseCase;
    private final AgentSimulator simulator;
    private final CallQualityJudge judge;
    private final AuditUseCase audit;
    private final SimulationProperties simulationProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AgentEvaluationService(AgentTestCaseRepository testCaseRepository, AgentEvalRepository evalRepository,
                                  AiAgentUseCase aiAgentUseCase, AgentSimulator simulator, CallQualityJudge judge,
                                  AuditUseCase audit, SimulationProperties simulationProperties,
                                  ApplicationEventPublisher eventPublisher, Clock clock) {
        this.testCaseRepository = testCaseRepository;
        this.evalRepository = evalRepository;
        this.aiAgentUseCase = aiAgentUseCase;
        this.simulator = simulator;
        this.judge = judge;
        this.audit = audit;
        this.simulationProperties = simulationProperties;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public List<AgentTestCase> findTestCases(long companyId, long agentId) {
        aiAgentUseCase.requireAgent(companyId, agentId);
        return testCaseRepository.findByAgent(companyId, agentId);
    }

    @Override
    public AgentTestCase createTestCase(long companyId, long agentId, SaveAgentTestCaseRequest request) {
        aiAgentUseCase.requireAgent(companyId, agentId);
        long id = testCaseRepository.create(toTestCase(companyId, agentId, request));
        return testCaseRepository.find(companyId, agentId, id);
    }

    @Override
    public AgentTestCase updateTestCase(long companyId, long agentId, long caseId, SaveAgentTestCaseRequest request) {
        requireTestCase(companyId, agentId, caseId);
        testCaseRepository.update(companyId, caseId, toTestCase(companyId, agentId, request));
        return testCaseRepository.find(companyId, agentId, caseId);
    }

    @Override
    public void deleteTestCase(long companyId, long agentId, long caseId) {
        requireTestCase(companyId, agentId, caseId);
        testCaseRepository.delete(companyId, caseId);
    }

    @Override
    public AgentEvalRun evaluate(long companyId, long agentId, Integer version) {
        AiAgent agent = aiAgentUseCase.requireAgentVersion(companyId, agentId, version);
        List<AgentTestCase> cases = testCaseRepository.findByAgent(companyId, agentId);
        if (cases.isEmpty()) {
            throw new ConflictException(ErrorCode.AGENT_EVAL_NO_TEST_CASES, agentId);
        }
        Instant now = clock.instant();
        if (evalRepository.hasRunningSince(companyId, agentId, now.minus(STALE_RUN))) {
            throw new ConflictException(ErrorCode.AGENT_EVAL_ALREADY_RUNNING, agentId);
        }
        ScenarioDefinition scenario = aiAgentUseCase.resolveScenario(companyId, agent).definition();
        long runId = evalRepository.createRun(AgentEvalRun.starting(companyId, agentId, version, cases.size(), now));
        audit.record(companyId, "AGENT_EVAL_START", "ai_agent", String.valueOf(agentId),
                "run " + runId + ", " + cases.size() + " cases, version " + (version != null ? version : "draft"));
        executor.execute(() -> run(runId, companyId, agent, scenario, cases));
        return evalRepository.findRun(companyId, agentId, runId);
    }

    @Override
    public List<AgentEvalRun> findRuns(long companyId, long agentId) {
        aiAgentUseCase.requireAgent(companyId, agentId);
        return evalRepository.findRuns(companyId, agentId, RUNS_LISTED);
    }

    @Override
    public AgentEvalRunDetail findRun(long companyId, long agentId, long runId) {
        AgentEvalRun run = evalRepository.findRun(companyId, agentId, runId);
        if (run == null) {
            throw new NotFoundException(ErrorCode.AGENT_EVAL_RUN_NOT_FOUND, runId);
        }
        return new AgentEvalRunDetail(run, evalRepository.findResults(runId));
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private void run(long runId, long companyId, AiAgent agent, ScenarioDefinition scenario, List<AgentTestCase> cases) {
        int passed = 0;
        int scored = 0;
        long scoreSum = 0;
        try {
            for (AgentTestCase testCase : cases) {
                AgentEvalResult result = runCase(runId, companyId, agent, scenario, testCase);
                evalRepository.addResult(result);
                if (result.passed()) {
                    passed++;
                }
                if (result.qaScore() != null) {
                    scored++;
                    scoreSum += result.qaScore();
                }
            }
            evalRepository.finishRun(runId, AgentEvalRunStatus.DONE, passed, scored > 0 ? (int) (scoreSum / scored) : null,
                    clock.instant(), null);
            log.info("Agent {} evaluation {} finished: {}/{} passed", agent.id(), runId, passed, cases.size());
            announce(companyId, agent.id(), runId, AgentEvalRunStatus.DONE, passed, cases.size());
        } catch (RuntimeException e) {
            log.warn("Agent {} evaluation {} broke off: {}", agent.id(), runId, e.getMessage());
            evalRepository.finishRun(runId, AgentEvalRunStatus.FAILED, passed, null, clock.instant(), truncate(e.getMessage()));
            announce(companyId, agent.id(), runId, AgentEvalRunStatus.FAILED, passed, cases.size());
        }
    }

    /**
     * Tells whoever is waiting on this run how it went — the insight gate holds a suggested
     * prompt change behind it (UNIVERSAL-OPERATOR-PLAN D.4). Best-effort: a listener that
     * throws must not turn a finished evaluation into a failed one, and the run is already
     * written by the time this is raised.
     */
    private void announce(long companyId, long agentId, long runId, AgentEvalRunStatus status, int passed, int total) {
        try {
            eventPublisher.publishEvent(new AgentEvalFinishedEvent(companyId, agentId, runId, status, passed, total));
        } catch (RuntimeException e) {
            log.warn("Agent {} evaluation {} finished, but a listener failed: {}", agentId, runId, e.getMessage());
        }
    }

    /** One case; a case that throws is a failed case, not a failed run. */
    private AgentEvalResult runCase(long runId, long companyId, AiAgent agent, ScenarioDefinition scenario,
                                    AgentTestCase testCase) {
        try {
            SimulationPersona persona = new SimulationPersona(testCase.name(), testCase.personaPrompt(),
                    withoutNulls(testCase.facts()), testCase.expectDisposition(), testCase.expectStage());
            String language = testCase.language() != null && !testCase.language().isBlank()
                    ? testCase.language() : agent.language();
            SimulatedCall call = simulator.simulate(companyId, scenario, agent, language, persona,
                    testCase.maxTurns(), simulationProperties.personaModel());
            List<String> problems = new ArrayList<>(call.problems());
            problems.addAll(expectationProblems(testCase, call));
            QualityVerdict verdict = call.transcript().isEmpty()
                    ? null : judge.evaluate(call.transcriptText(), scenario, call.disposition());
            if (verdict != null && verdict.guardrailViolation()) {
                problems.add("guardrail: " + verdict.note());
            }
            return new AgentEvalResult(null, runId, testCase.id(), testCase.name(), problems.isEmpty(),
                    call.disposition() != null ? call.disposition().name() : null, call.stage(), problems,
                    call.transcriptText(), verdict != null ? verdict.score() : null,
                    verdict != null && verdict.guardrailViolation());
        } catch (RuntimeException e) {
            log.warn("Evaluation case '{}' of agent {} failed: {}", testCase.name(), agent.id(), e.getMessage());
            return new AgentEvalResult(null, runId, testCase.id(), testCase.name(), false, null, null,
                    List.of("error: " + truncate(e.getMessage())), "", null, false);
        }
    }

    /** What the case asked for, against what the call did. */
    private static List<String> expectationProblems(AgentTestCase testCase, SimulatedCall call) {
        List<String> problems = new ArrayList<>();
        String disposition = call.disposition() != null ? call.disposition().name() : null;
        if (isSet(testCase.expectDisposition()) && !testCase.expectDisposition().equalsIgnoreCase(disposition)) {
            problems.add("expected disposition " + testCase.expectDisposition() + ", got " + disposition);
        }
        if (isSet(testCase.expectStage()) && !testCase.expectStage().equalsIgnoreCase(call.stage())) {
            problems.add("expected stage " + testCase.expectStage() + ", got " + call.stage());
        }
        String agentSaid = call.transcript().stream()
                .filter(line -> line.startsWith("AGENT: "))
                .map(line -> line.toLowerCase(Locale.ROOT))
                .collect(Collectors.joining("\n"));
        for (String phrase : testCase.mustSay()) {
            if (isSet(phrase) && !agentSaid.contains(phrase.trim().toLowerCase(Locale.ROOT))) {
                problems.add("never said: " + phrase.trim());
            }
        }
        for (String phrase : testCase.mustNotSay()) {
            if (isSet(phrase) && agentSaid.contains(phrase.trim().toLowerCase(Locale.ROOT))) {
                problems.add("said what it must not: " + phrase.trim());
            }
        }
        return problems;
    }

    private void requireTestCase(long companyId, long agentId, long caseId) {
        if (testCaseRepository.find(companyId, agentId, caseId) == null) {
            throw new NotFoundException(ErrorCode.AGENT_TEST_CASE_NOT_FOUND, caseId);
        }
    }

    private static AgentTestCase toTestCase(long companyId, long agentId, SaveAgentTestCaseRequest request) {
        return new AgentTestCase(null, companyId, agentId, request.name().trim(), request.personaPrompt().trim(),
                request.facts(), blankToNull(request.language()), blankToNull(request.expectDisposition()),
                blankToNull(request.expectStage()), request.mustSay(), request.mustNotSay(),
                request.maxTurns() != null ? request.maxTurns() : AgentTestCase.DEFAULT_MAX_TURNS, null);
    }

    /** A persona's facts may not hold nulls; a fact the case left empty is simply absent. */
    private static Map<String, Object> withoutNulls(Map<String, Object> facts) {
        Map<String, Object> clean = new LinkedHashMap<>();
        facts.forEach((name, value) -> {
            if (value != null) {
                clean.put(name, value);
            }
        });
        return clean;
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return isSet(value) ? value.trim() : null;
    }

    private static String truncate(String message) {
        String text = String.valueOf(message);
        return text.length() > MAX_ERROR_CHARS ? text.substring(0, MAX_ERROR_CHARS) : text;
    }
}
