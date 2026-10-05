package uz.murodjon.robotcallv2.dialer.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.agent.dialog.CallContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** NEXT-PLAN A.8: per-call {@code agentOverrides} in a target's context data. */
class CallContextAgentOverridesTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String JSON = """
            {"goal": "Qarzni eslatish",
             "agentOverrides": {
               "firstMessage": "Assalomu alaykum, Sardor aka!",
               "systemPrompt": "Mijoz VIP, muloyimroq bo'ling.",
               "variables": {"orderNumber": "ORD-9981", "orderAmount": 450000}}}
            """;

    @Test
    void variablesBecomeFactsAndPromptIsAppendedToGoal() {
        CallContext context = CallContextFactory.readContextData(OBJECT_MAPPER, JSON, null, List.of());

        assertThat(context.fact("orderNumber")).isEqualTo("ORD-9981");
        assertThat(context.fact("orderAmount")).isEqualTo("450000");
        assertThat(context.goal()).startsWith("Qarzni eslatish").contains("Mijoz VIP");
    }

    @Test
    void firstMessageOverrideIsReadOnlyFromOverrides() {
        assertThat(CallContextFactory.readFirstMessageOverride(OBJECT_MAPPER, JSON)).isEqualTo("Assalomu alaykum, Sardor aka!");
        assertThat(CallContextFactory.readFirstMessageOverride(OBJECT_MAPPER, "{\"goal\": \"x\"}")).isNull();
        assertThat(CallContextFactory.readFirstMessageOverride(OBJECT_MAPPER, null)).isNull();
    }

    @Test
    void contextWithoutOverridesIsUnchanged() {
        CallContext context = CallContextFactory.readContextData(OBJECT_MAPPER, "{\"goal\": \"Qarzni eslatish\"}", null, List.of());

        assertThat(context.facts()).isEmpty();
        assertThat(context.goal()).isEqualTo("Qarzni eslatish");
    }
}
