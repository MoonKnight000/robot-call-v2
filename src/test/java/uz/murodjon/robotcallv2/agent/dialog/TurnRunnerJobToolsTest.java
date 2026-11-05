package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageAction;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.scenario.domain.enums.StageType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Call 15: the CREATE_TASK job turn called recordPaymentPromise and transitionTo, and no CRM task was made. */
class TurnRunnerJobToolsTest {

    private static ToolCallback tool(String name) {
        ToolCallback tool = mock(ToolCallback.class);
        when(tool.getToolDefinition()).thenReturn(ToolDefinition.builder()
                .name(name).description(name).inputSchema("{}").build());
        return tool;
    }

    private static DialogSession sessionIn(String state, int step) {
        StageAction task = new StageAction("createFollowUpTask", null, null, null, null, null, null, null, null, null);
        ScenarioDefinition scenario = ScenarioDefinition.builder()
                .stages(List.of(
                        StageDef.builder().id("CREATE_TASK").type(StageType.FUNCTION).action(task)
                                .allowedTransitions(List.of("CONFIRMATION")).build(),
                        StageDef.builder().id("CONFIRMATION").type(StageType.CONVERSATION).build()))
                .rolePrompt("Prompt")
                .build();
        DialogSession s = DialogSession.builder("chan-1", "uz-UZ")
                .context(new CallContext(Map.of(), "goal"))
                .scenario(scenario)
                .companyId(1L)
                .build();
        s.setState(state);
        s.setStageActionStep(step);
        return s;
    }

    private final List<ToolCallback> all = List.of(tool("recordPaymentPromise"), tool("createFollowUpTask"),
            tool("transitionTo"), tool("endCall"));

    @Test
    void theJobStepOffersOnlyTheJobsOwnTool() {
        List<ToolCallback> offered = TurnRunner.findJobTools(sessionIn("CREATE_TASK", 1), all);

        assertThat(offered).extracting(t -> t.getToolDefinition().name()).containsExactly("createFollowUpTask");
    }

    @Test
    void movingOnAndOtherStagesKeepTheWholeSet() {
        assertThat(TurnRunner.findJobTools(sessionIn("CREATE_TASK", 2), all)).hasSize(4);
        assertThat(TurnRunner.findJobTools(sessionIn("CONFIRMATION", 1), all)).hasSize(4);
        // A job whose tool this call does not offer is left to the whole set rather than to none.
        assertThat(TurnRunner.findJobTools(sessionIn("CREATE_TASK", 1), List.of(tool("endCall")))).hasSize(1);
    }
}
