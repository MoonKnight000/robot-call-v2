package uz.murodjon.robotcallv2.agent.dialog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.aiagent.AiAgentFixtures;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.WorkflowFunction;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmSettingsUseCase;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmSettings;
import uz.murodjon.robotcallv2.integration.application.port.input.IntegrationToolUseCase;
import uz.murodjon.robotcallv2.tool.application.port.input.ToolUseCase;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolEffect;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PreCallWorkflowRunnerTest {

    private static final long COMPANY = 7;

    private final HttpToolExecutor executor = mock(HttpToolExecutor.class);
    private final ToolUseCase toolUseCase = mock(ToolUseCase.class);
    private final IntegrationToolUseCase integrationToolUseCase = mock(IntegrationToolUseCase.class);
    private final CrmSettingsUseCase crmSettingsUseCase = mock(CrmSettingsUseCase.class);
    private PreCallWorkflowRunner runner;

    @BeforeEach
    void setUp() {
        when(crmSettingsUseCase.findByCompanyId(COMPANY))
                .thenReturn(new CrmSettings(COMPANY, null, null, null, null, null, null, null, null));
        when(toolUseCase.findAgentToolDefinitions(eq(COMPANY), any(Long.class))).thenReturn(List.of());
        runner = new PreCallWorkflowRunner(executor, toolUseCase, integrationToolUseCase, crmSettingsUseCase,
                new ObjectMapper());
    }

    private static Tool tool(String name, ToolEffect effect) {
        Tool tool = mock(Tool.class);
        when(tool.name()).thenReturn(name);
        when(tool.effect()).thenReturn(effect);
        return tool;
    }

    private static WorkflowFunction fn(String id, String toolName, Map<String, String> inputs,
                                       Map<String, String> outputs, String... dependsOn) {
        return new WorkflowFunction(id, toolName, inputs, outputs, List.of(dependsOn), true);
    }

    private AiAgent agentWith(WorkflowFunction... functions) {
        return AiAgentFixtures.agent(1, COMPANY, 1, "uz", "voice").withPreCallFunctions(List.of(functions));
    }

    private Map<String, Object> run(AiAgent agent) {
        return runner.run(agent, COMPANY, "ch-1", 5, "uz", "+998901234567", Map.of());
    }

    @Test
    void anAgentWithNoFunctionsProducesNothingAndTouchesNothing() {
        assertThat(run(AiAgentFixtures.agent(1, COMPANY, 1, "uz", "voice"))).isEmpty();

        verify(executor, never()).executeForTest(any(), anyMap(), anyMap());
    }

    @Test
    void aLaterFunctionReadsWhatAnEarlierOneProduced() {
        Tool findLead = tool("find_lead", ToolEffect.READ);
        Tool balance = tool("get_balance", ToolEffect.READ);
        when(integrationToolUseCase.findEnabledHttpTools(COMPANY)).thenReturn(List.of(findLead, balance));
        when(executor.executeForTest(eq(findLead), anyMap(), eq(Map.of("search", "901234567"))))
                .thenReturn(new ToolTestRun("ok", "{\"data\":[{\"id\":42}]}"));
        when(executor.previewMapping("{\"data\":[{\"id\":42}]}", Map.of("lead_id", "$.data[0].id")))
                .thenReturn(new ObjectMapper().valueToTree(Map.of("lead_id", 42)));
        when(executor.executeForTest(eq(balance), anyMap(), eq(Map.of("leadId", 42))))
                .thenReturn(new ToolTestRun("ok", "{\"debt\":1500}"));
        when(executor.previewMapping("{\"debt\":1500}", Map.of("debt", "$.debt")))
                .thenReturn(new ObjectMapper().valueToTree(Map.of("debt", 1500)));

        Map<String, Object> produced = run(agentWith(
                fn("lead", "find_lead", Map.of("search", "{{phone_last9}}"), Map.of("lead_id", "$.data[0].id")),
                fn("debt", "get_balance", Map.of("leadId", "{{lead_id}}"), Map.of("debt", "$.debt"), "lead")));

        assertThat(produced).containsEntry("lead_id", 42).containsEntry("debt", 1500);
    }

    @Test
    void aFailedFunctionTakesDownOnlyWhatWaitsForIt() {
        Tool broken = tool("find_lead", ToolEffect.READ);
        Tool independent = tool("get_tariff", ToolEffect.READ);
        Tool dependent = tool("get_balance", ToolEffect.READ);
        when(integrationToolUseCase.findEnabledHttpTools(COMPANY)).thenReturn(List.of(broken, independent, dependent));
        when(executor.executeForTest(eq(broken), anyMap(), anyMap())).thenThrow(new IllegalStateException("down"));
        when(executor.executeForTest(eq(independent), anyMap(), anyMap())).thenReturn(new ToolTestRun("ok", "{\"t\":1}"));
        when(executor.previewMapping("{\"t\":1}", Map.of("tariff", "$.t")))
                .thenReturn(new ObjectMapper().valueToTree(Map.of("tariff", 1)));

        Map<String, Object> produced = run(agentWith(
                fn("lead", "find_lead", Map.of(), Map.of("lead_id", "$.id")),
                fn("tariff", "get_tariff", Map.of(), Map.of("tariff", "$.t")),
                fn("debt", "get_balance", Map.of(), Map.of("debt", "$.debt"), "lead")));

        assertThat(produced).containsOnlyKeys("tariff");
        verify(executor, never()).executeForTest(eq(dependent), anyMap(), anyMap());
    }

    @Test
    void aToolThatWritesIsNeverRunBeforeTheCall() {
        Tool write = tool("create_lead", ToolEffect.WRITE);
        when(integrationToolUseCase.findEnabledHttpTools(COMPANY)).thenReturn(List.of(write));

        assertThat(run(agentWith(fn("make", "create_lead", Map.of(), Map.of())))).isEmpty();

        verify(executor, never()).executeForTest(any(), anyMap(), anyMap());
    }

    @Test
    void aFunctionWhoseInputHasNoValueIsSkipped() {
        Tool findLead = tool("find_lead", ToolEffect.READ);
        when(integrationToolUseCase.findEnabledHttpTools(COMPANY)).thenReturn(List.of(findLead));

        assertThat(run(agentWith(fn("lead", "find_lead", Map.of("id", "{{nobody}}"), Map.of())))).isEmpty();

        verify(executor, never()).executeForTest(any(), anyMap(), anyMap());
    }

    @Test
    void aTemplateThatIsOneVariableKeepsItsType() {
        assertThat(PreCallWorkflowRunner.resolve("{{n}}", Map.of("n", 42))).isEqualTo(42);
        assertThat(PreCallWorkflowRunner.resolve("id-{{n}}", Map.of("n", 42))).isEqualTo("id-42");
        assertThat(PreCallWorkflowRunner.resolve("id-{{missing}}", Map.of("n", 42))).isNull();
    }
}
