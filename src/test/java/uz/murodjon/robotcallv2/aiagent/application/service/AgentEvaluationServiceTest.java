package uz.murodjon.robotcallv2.aiagent.application.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import uz.murodjon.robotcallv2.agent.ari.VoiceCallSimulator;
import uz.murodjon.robotcallv2.agent.dialog.AgentSimulator;
import uz.murodjon.robotcallv2.agent.dialog.SimulatedCall;
import uz.murodjon.robotcallv2.agent.dialog.SimulationProperties;
import uz.murodjon.robotcallv2.agent.summary.CallQualityJudge;
import uz.murodjon.robotcallv2.aiagent.AiAgentFixtures;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentEvalCaseSummary;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AgentEvalRepository;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AgentTestCaseRepository;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentEvalResult;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentEvalRun;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentTestCase;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentEvalMode;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentEvalRunStatus;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentEvaluationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");
    private static final long COMPANY_ID = 1L;
    private static final long AGENT_ID = 7L;
    private static final long RUN_ID = 5L;

    private final AgentTestCaseRepository testCaseRepository = mock(AgentTestCaseRepository.class);
    private final AgentEvalRepository evalRepository = mock(AgentEvalRepository.class);
    private final AiAgentUseCase aiAgentUseCase = mock(AiAgentUseCase.class);
    private final AgentSimulator simulator = mock(AgentSimulator.class);
    private final AgentEvaluationService service = new AgentEvaluationService(testCaseRepository, evalRepository,
            aiAgentUseCase, simulator, mock(VoiceCallSimulator.class), mock(CallQualityJudge.class),
            mock(AuditUseCase.class), new SimulationProperties(null, 0, "uz", 12, "persona-model"),
            mock(ApplicationEventPublisher.class), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void everyCaseIsPlayedRepeatTimesAndEveryAttemptIsKept() {
        givenAgentWith(testCase(Map.of(), List.of()));
        when(simulator.simulate(anyLong(), any(), any(), anyString(), any(), anyInt(), anyString(), isNull()))
                .thenReturn(new SimulatedCall(List.of("AGENT: Salom"), null, null, List.of()));

        service.evaluate(COMPANY_ID, AGENT_ID, null, AgentEvalMode.TEXT, 3);

        verify(evalRepository, timeout(2000).times(3)).addResult(any());
        verify(evalRepository, timeout(2000)).finishRun(eq(RUN_ID), eq(AgentEvalRunStatus.DONE), eq(3), isNull(),
                any(), isNull());
    }

    @Test
    void aCaseThatExpectsAToolIsPlayedWithItsMocksAndFailsWhenTheToolIsNeverCalled() {
        Map<String, String> mocks = Map.of("crm_lookup", "{\"debt\":500000}");
        givenAgentWith(testCase(mocks, List.of("crm_lookup")));
        when(simulator.simulate(anyLong(), any(), any(), anyString(), any(), anyInt(), anyString(), eq(mocks)))
                .thenReturn(new SimulatedCall(List.of("AGENT: Salom"), null, null, List.of()));

        service.evaluate(COMPANY_ID, AGENT_ID, null, AgentEvalMode.TEXT, 1);

        ArgumentCaptor<AgentEvalResult> result = ArgumentCaptor.forClass(AgentEvalResult.class);
        verify(evalRepository, timeout(2000)).addResult(result.capture());
        assertThat(result.getValue().passed()).isFalse();
        assertThat(result.getValue().problems()).contains("never called tool: crm_lookup");
    }

    @Test
    void aToolCallInTheTranscriptMeetsTheExpectation() {
        Map<String, String> mocks = Map.of("crm_lookup", "{\"debt\":500000}");
        givenAgentWith(testCase(mocks, List.of("crm_lookup")));
        when(simulator.simulate(anyLong(), any(), any(), anyString(), any(), anyInt(), anyString(), eq(mocks)))
                .thenReturn(new SimulatedCall(List.of("TOOL: crm_lookup {\"phone\":\"+998901234567\"}",
                        "AGENT: Qarzingiz 500 000 so'm"), null, null, List.of()));

        service.evaluate(COMPANY_ID, AGENT_ID, null, AgentEvalMode.TEXT, 1);

        ArgumentCaptor<AgentEvalResult> result = ArgumentCaptor.forClass(AgentEvalResult.class);
        verify(evalRepository, timeout(2000)).addResult(result.capture());
        assertThat(result.getValue().passed()).isTrue();
    }

    @Test
    void repeatOutsideOneToTenIsRefused() {
        assertThatThrownBy(() -> service.evaluate(COMPANY_ID, AGENT_ID, null, AgentEvalMode.TEXT, 11))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.AGENT_EVAL_REPEAT_INVALID));
    }

    @Test
    void aCaseThatPassedSomeAttemptsAndNotOthersIsFlaky() {
        when(evalRepository.findRun(COMPANY_ID, AGENT_ID, RUN_ID)).thenReturn(run());
        when(evalRepository.findResults(RUN_ID)).thenReturn(List.of(result(true, 1), result(false, 2)));

        AgentEvalCaseSummary summary = service.findRun(COMPANY_ID, AGENT_ID, RUN_ID).cases().getFirst();

        assertThat(summary.attempts()).isEqualTo(2);
        assertThat(summary.passRate()).isEqualTo(50.0);
        assertThat(summary.flaky()).isTrue();
    }

    private void givenAgentWith(AgentTestCase testCase) {
        when(aiAgentUseCase.requireAgentVersion(COMPANY_ID, AGENT_ID, null))
                .thenReturn(AiAgentFixtures.agent(AGENT_ID, COMPANY_ID, 3L, "uz", null));
        ScenarioDefinition definition = new ScenarioDefinition(List.of(), List.of(), List.of(), List.of(),
                "system prompt", List.of(), "disclosure");
        when(aiAgentUseCase.resolveScenario(eq(COMPANY_ID), any()))
                .thenReturn(new Scenario(3L, "s", 1, "S", "", false, true, definition, NOW, 1L));
        when(testCaseRepository.findByAgent(COMPANY_ID, AGENT_ID)).thenReturn(List.of(testCase));
        when(evalRepository.createRun(any())).thenReturn(RUN_ID);
    }

    private static AgentTestCase testCase(Map<String, String> toolMocks, List<String> expectTools) {
        return new AgentTestCase(11L, COMPANY_ID, AGENT_ID, "qarz so'raydi", "Siz qarzdorsiz", Map.of(), null,
                null, null, List.of(), List.of(), 6, NOW, toolMocks, expectTools);
    }

    private static AgentEvalRun run() {
        return new AgentEvalRun(RUN_ID, COMPANY_ID, AGENT_ID, null, AgentEvalMode.TEXT, "hash",
                AgentEvalRunStatus.DONE, 2, 1, null, null, NOW, NOW, 2);
    }

    private static AgentEvalResult result(boolean passed, int attempt) {
        return new AgentEvalResult(null, RUN_ID, 11L, "qarz so'raydi", passed, null, null,
                passed ? List.of() : List.of("never said: rahmat"), "", null, false, attempt);
    }
}
