package uz.murodjon.robotcallv2.callflow.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlowGraph;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlowNode;
import uz.murodjon.robotcallv2.callflow.domain.enums.CallFlowNodeType;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CallFlowValidatorTest {

    private static CallFlowNode node(CallFlowNodeType type, Map<String, String> prompt, Map<String, String> options,
                                     Map<String, String> exits) {
        return new CallFlowNode(type, prompt, null, options, exits, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    private static CallFlowNode play(String next) {
        return node(CallFlowNodeType.PLAY, Map.of("uz-UZ", "Assalomu alaykum"), null,
                next != null ? Map.of("next", next) : null);
    }

    private static CallFlowNode hangup() {
        return node(CallFlowNodeType.HANGUP, null, null, null);
    }

    private static CallFlowNode queue(long queueId, String answered) {
        return new CallFlowNode(CallFlowNodeType.QUEUE, null, null, null, Map.of("answered", answered), null, null,
                null, null, null, queueId, null, null, null, null, null, null, null, null);
    }

    private static CallFlowGraph graph(String entry, Map<String, CallFlowNode> nodes) {
        return new CallFlowGraph(entry, new LinkedHashMap<>(nodes));
    }

    @Test
    void aCompleteFlowHasNoProblems() {
        Map<String, CallFlowNode> nodes = new LinkedHashMap<>();
        nodes.put("greet", play("menu"));
        nodes.put("menu", node(CallFlowNodeType.MENU, Map.of("uz-UZ", "1 ni bosing"),
                Map.of("1", "operators", "*", "menu"), Map.of("timeout", "operators")));
        nodes.put("operators", queue(1, "bye"));
        nodes.put("bye", hangup());

        assertThat(CallFlowValidator.findProblems(graph("greet", nodes))).isEmpty();
    }

    @Test
    void anEmptyFlowIsRefused() {
        assertThat(CallFlowValidator.findProblems(graph("x", Map.of()))).containsExactly("a flow needs at least one node");
    }

    @Test
    void theEntryMustBeANode() {
        assertThat(CallFlowValidator.findProblems(graph("missing", Map.of("bye", hangup()))))
                .contains("entry 'missing' is not a node");
    }

    @Test
    void anExitMustLeadToANode() {
        assertThat(CallFlowValidator.findProblems(graph("greet", Map.of("greet", play("nowhere")))))
                .contains("greet: exit 'next' leads to missing node 'nowhere'");
    }

    @Test
    void aNodeMayOnlyUseItsOwnExits() {
        CallFlowNode wrong = node(CallFlowNodeType.PLAY, Map.of("uz-UZ", "x"), null, Map.of("answered", "bye"));

        assertThat(CallFlowValidator.findProblems(graph("greet", Map.of("greet", wrong, "bye", hangup()))))
                .contains("greet: PLAY has no exit 'answered'");
    }

    @Test
    void aMenuNeedsKeypadOptions() {
        CallFlowNode noOptions = node(CallFlowNodeType.MENU, Map.of("uz-UZ", "x"), null, null);
        CallFlowNode badKey = node(CallFlowNodeType.MENU, Map.of("uz-UZ", "x"), Map.of("11", "bye"), null);

        assertThat(CallFlowValidator.findProblems(graph("m", Map.of("m", noOptions))))
                .contains("m: a menu needs at least one option");
        assertThat(CallFlowValidator.findProblems(graph("m", Map.of("m", badKey, "bye", hangup()))))
                .contains("m: option key '11' must be one of 0-9, * or #");
    }

    @Test
    void languageOptionsMustBeLanguageTags() {
        CallFlowNode language = node(CallFlowNodeType.LANGUAGE_SELECT, Map.of("uz-UZ", "x"),
                Map.of("1", "uzbek"), null);

        assertThat(CallFlowValidator.findProblems(graph("l", Map.of("l", language))))
                .contains("l: option 1 must name a language like uz-UZ");
    }

    @Test
    void eachTypeNeedsWhatItPointsAt() {
        CallFlowNode schedule = node(CallFlowNodeType.SCHEDULE, null, null, Map.of("open", "bye"));
        CallFlowNode forward = new CallFlowNode(CallFlowNodeType.FORWARD_EXTERNAL, null, null, null, null, null, null,
                null, null, null, null, null, null, null, "not-a-number", null, null, null, null);
        CallFlowNode extension = new CallFlowNode(CallFlowNodeType.EXTENSION, null, null, null, null, null, null,
                null, null, null, null, null, null, List.of(1L, 2L), null, null, null, null, null);

        List<String> problems = CallFlowValidator.findProblems(graph("s",
                Map.of("s", schedule, "f", forward, "e", extension, "bye", hangup())));

        assertThat(problems).contains(
                "s: scheduleId is required",
                "s: SCHEDULE needs both an 'open' and a 'closed' exit",
                "f: phoneNumber must be a phone number",
                "e: EXTENSION rings exactly one operator (userIds)");
    }

    @Test
    void aWebhookRouterMayNameItsOwnExits() {
        CallFlowNode router = new CallFlowNode(CallFlowNodeType.WEBHOOK_ROUTER, null, null, null,
                Map.of("debtor", "bye", "default", "bye"), null, null, null, null, null, null, null, null, null, null,
                null, "https://crm.example.uz/route", null, null);

        assertThat(CallFlowValidator.findProblems(graph("w", Map.of("w", router, "bye", hangup())))).isEmpty();
    }

    @Test
    void hangupHasNoExits() {
        CallFlowNode leaky = node(CallFlowNodeType.HANGUP, null, null, Map.of("next", "h"));

        assertThat(CallFlowValidator.findProblems(graph("h", Map.of("h", leaky)))).contains("h: HANGUP has no exits");
    }

    @Test
    void validateThrowsWithEveryProblemListed() {
        assertThatThrownBy(() -> CallFlowValidator.validate(graph("greet", Map.of("greet", play("nowhere")))))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.errors())
                        .containsExactly("greet: exit 'next' leads to missing node 'nowhere'"));
    }
}
