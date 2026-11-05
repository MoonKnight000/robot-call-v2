package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A stage move the model typed into its reply is run like a real call (calls 12 and 13). */
class TypedToolCallsTest {

    @Test
    void theStageIsReadFromEveryTypedFormSeenOnACall() {
        assertThat(TypedToolCalls.findTypedTransition(
                "Hozir vaqtingiz bormi?ВДdefault_api:transitionTo{nextStage:DEBT_NOTICE}")).isEqualTo("DEBT_NOTICE");
        assertThat(TypedToolCalls.findTypedTransition("roz<transitionTo nextStage\"REASON_INQUIRY\"/>"))
                .isEqualTo("REASON_INQUIRY");
        assertThat(TypedToolCalls.findTypedTransition("transitionTo(nextStage=\"PAYMENT_DATE\")")).isEqualTo("PAYMENT_DATE");
    }

    @Test
    void ordinarySpeechHasNoTypedTransition() {
        assertThat(TypedToolCalls.findTypedTransition("Assalomu alaykum, Akmal aka, yaxshimisiz?")).isNull();
        assertThat(TypedToolCalls.findTypedTransition(null)).isNull();
    }

    @Test
    void theTypedMoveIsAddedOnceAndNeverOverAProperCall() {
        List<AssistantMessage.ToolCall> calls = new ArrayList<>();
        TypedToolCalls.addTypedTransition("ch", "Ha.default_api:transitionTo{nextStage:DEBT_NOTICE}", calls);

        assertThat(calls).singleElement().satisfies(call -> {
            assertThat(call.name()).isEqualTo("transitionTo");
            assertThat(call.arguments()).isEqualTo("{\"nextStage\":\"DEBT_NOTICE\"}");
        });

        List<AssistantMessage.ToolCall> proper = new ArrayList<>(List.of(
                new AssistantMessage.ToolCall("1", "function", "transitionTo", "{\"nextStage\":\"PAYMENT_DATE\"}")));
        TypedToolCalls.addTypedTransition("ch", "transitionTo{nextStage:DEBT_NOTICE}", proper);

        assertThat(proper).singleElement().satisfies(call -> assertThat(call.arguments()).contains("PAYMENT_DATE"));
    }
}
