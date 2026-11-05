package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunction;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentFunctionTiming;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentFunctionType;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolHttpConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The agent's function timing over a tool's own "wait for result". */
class AgentFunctionPlanTest {

    private static Tool tool(long id, boolean waitForResult) {
        return new Tool(id, 1L, "createFollowUpTask", "Creates a CRM task.",
                new ToolHttpConfig("https://crm.example/tasks", "POST", null, null, null, null, null, null, 0, null,
                        waitForResult, null),
                null, null, null, null, 0, null, null, null, null, null);
    }

    private static AgentFunction custom(long toolId, AgentFunctionTiming timing) {
        return AgentFunction.builder().id("fn_" + toolId).type(AgentFunctionType.CUSTOM).toolId(toolId)
                .timing(timing).build();
    }

    @Test
    void backgroundStopsTheTurnFromWaitingForAToolThatWould() {
        Tool applied = AgentFunctionPlan.applyTiming(tool(7L, true), custom(7L, AgentFunctionTiming.BACKGROUND));

        assertThat(applied.http().waitForResult()).isFalse();
    }

    @Test
    void inTurnMakesTheTurnWaitForAToolThatWouldNot() {
        Tool applied = AgentFunctionPlan.applyTiming(tool(7L, false), custom(7L, AgentFunctionTiming.IN_TURN));

        assertThat(applied.http().waitForResult()).isTrue();
    }

    @Test
    void noTimingOrNoFunctionKeepsTheToolsOwnSetting() {
        Tool waits = tool(7L, true);
        Tool doesNotWait = tool(8L, false);

        assertThat(AgentFunctionPlan.applyTiming(waits, custom(7L, null))).isSameAs(waits);
        assertThat(AgentFunctionPlan.applyTiming(doesNotWait, null)).isSameAs(doesNotWait);
    }

    @Test
    void findsTheCustomFunctionOfATool() {
        AgentFunction function = custom(7L, AgentFunctionTiming.BACKGROUND);
        AiAgent agent = AiAgent.builder().name("Agent").functions(List.of(custom(5L, null), function)).build();

        assertThat(AgentFunctionPlan.findCustomFunction(agent, tool(7L, true))).isEqualTo(function);
        assertThat(AgentFunctionPlan.findCustomFunction(agent, tool(9L, true))).isNull();
        assertThat(AgentFunctionPlan.findCustomFunction(AiAgent.builder().name("Old").build(), tool(7L, true))).isNull();
    }
}
