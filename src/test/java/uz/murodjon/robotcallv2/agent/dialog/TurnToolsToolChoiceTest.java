package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
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

/** Gemini is told which declared tools the stage may call, so a weak model cannot call the others (calls 14, 15). */
class TurnToolsToolChoiceTest {

    private static ToolCallback tool(String name) {
        ToolCallback tool = mock(ToolCallback.class);
        when(tool.getToolDefinition()).thenReturn(ToolDefinition.builder()
                .name(name).description(name).inputSchema("{}").build());
        return tool;
    }

    private static TurnTools turnTools(boolean restrict) {
        DialogProperties dialogProperties = mock(DialogProperties.class);
        when(dialogProperties.stateScopedTools()).thenReturn(true);
        return new TurnTools(dialogProperties, null, null, null, null, null, null, null, null,
                new PromptCacheProperties(true, true, true, restrict), null, true);
    }

    private static DialogSession sessionIn(String state) {
        StageAction promise = new StageAction("recordPaymentPromise", null, null, null, null, null, null, null, null, null);
        ScenarioDefinition scenario = ScenarioDefinition.builder()
                .stages(List.of(
                        // An explicit empty list: no scenario tool here, as in debt-collection's DEBT_NOTICE.
                        StageDef.builder().id("DEBT_NOTICE").type(StageType.CONVERSATION).allowedTools(List.of())
                                .allowedTransitions(List.of("PAYMENT_DATE")).build(),
                        StageDef.builder().id("PAYMENT_DATE").type(StageType.CONVERSATION).allowedTools(List.of())
                                .allowedTransitions(List.of("RECORD_PROMISE")).build(),
                        StageDef.builder().id("RECORD_PROMISE").type(StageType.FUNCTION).action(promise)
                                .allowedTransitions(List.of()).build()))
                .rolePrompt("Prompt")
                .build();
        DialogSession s = DialogSession.builder("chan-1", "uz-UZ")
                .context(new CallContext(Map.of(), "goal"))
                .scenario(scenario)
                .companyId(1L)
                .build();
        s.setState(state);
        return s;
    }

    private final List<ToolCallback> declared = List.of(tool("transitionTo"), tool("endCall"),
            tool("recordPaymentPromise"), tool("createFollowUpTask"));

    @Test
    void aStageThatDoesNotOfferTheJobKeepsItOutOfReach() {
        GoogleGenAiChatOptions.ToolChoice choice = turnTools(true)
                .findToolChoice(sessionIn("DEBT_NOTICE"), declared, "gemini-3.5-flash-lite");

        assertThat(choice.mode()).isEqualTo(GoogleGenAiChatOptions.ToolChoice.Mode.VALIDATED);
        assertThat(choice.allowedFunctionNames()).contains("transitionTo", "endCall")
                .doesNotContain("recordPaymentPromise", "createFollowUpTask");
    }

    @Test
    void theStageBeforeTheJobMayCallItInline() {
        GoogleGenAiChatOptions.ToolChoice choice = turnTools(true)
                .findToolChoice(sessionIn("PAYMENT_DATE"), declared, "gemini-3.5-flash-lite");

        assertThat(choice.allowedFunctionNames()).contains("recordPaymentPromise").doesNotContain("createFollowUpTask");
    }

    @Test
    void nothingIsRestrictedOffGeminiOrWhenSwitchedOffOrForASingleJob() {
        assertThat(turnTools(true).findToolChoice(sessionIn("DEBT_NOTICE"), declared, "gpt-5-mini")).isNull();
        assertThat(turnTools(false).findToolChoice(sessionIn("DEBT_NOTICE"), declared, "gemini-3.5-flash-lite")).isNull();
        assertThat(turnTools(true).findToolChoice(sessionIn("DEBT_NOTICE"),
                List.of(tool("createFollowUpTask")), "gemini-3.5-flash-lite")).isNull();
        assertThat(turnTools(true).findToolChoice(sessionIn("DEBT_NOTICE"), List.of(), "gemini-3.5-flash-lite")).isNull();
    }
}
