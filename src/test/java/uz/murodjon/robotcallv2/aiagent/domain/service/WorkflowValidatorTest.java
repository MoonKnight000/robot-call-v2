package uz.murodjon.robotcallv2.aiagent.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.aiagent.domain.entity.WorkflowFunction;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowValidatorTest {

    private static WorkflowFunction fn(String id, String... dependsOn) {
        return new WorkflowFunction(id, "tool_" + id, Map.of(), Map.of(), List.of(dependsOn), true);
    }

    @Test
    void anEmptySlotIsFine() {
        assertThatCode(() -> WorkflowValidator.validate(List.of())).doesNotThrowAnyException();
    }

    @Test
    void independentFunctionsAndAChainAreFine() {
        assertThatCode(() -> WorkflowValidator.validate(List.of(fn("find_lead"), fn("find_contract"),
                fn("balance", "find_contract"), fn("history", "find_lead", "balance")))).doesNotThrowAnyException();
    }

    @Test
    void everyProblemIsReportedNotOnlyTheFirst() {
        List<WorkflowFunction> functions = List.of(
                new WorkflowFunction("a", "tool", Map.of(), Map.of("1bad", "data.id"), List.of("ghost"), true),
                fn("a"));

        assertThatThrownBy(() -> WorkflowValidator.validate(functions))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.WORKFLOW_INVALID);
                    assertThat(e.errors()).hasSizeGreaterThanOrEqualTo(4)
                            .anyMatch(error -> error.contains("used twice"))
                            .anyMatch(error -> error.contains("1bad"))
                            .anyMatch(error -> error.contains("must be a path starting with $"))
                            .anyMatch(error -> error.contains("ghost"));
                });
    }

    @Test
    void aFunctionCannotWaitOnItself() {
        assertThatThrownBy(() -> WorkflowValidator.validate(List.of(fn("a", "a"))))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.errors()).anyMatch(error -> error.contains("depends on itself")));
    }

    @Test
    void aLoopIsRefused() {
        assertThatThrownBy(() -> WorkflowValidator.validate(List.of(fn("a", "c"), fn("b", "a"), fn("c", "b"))))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.errors()).anyMatch(error -> error.contains("loop")));
    }

    @Test
    void aChainOfFourIsTheLongestAllowed() {
        assertThatCode(() -> WorkflowValidator.validate(
                List.of(fn("a"), fn("b", "a"), fn("c", "b"), fn("d", "c")))).doesNotThrowAnyException();
        assertThatThrownBy(() -> WorkflowValidator.validate(
                List.of(fn("a"), fn("b", "a"), fn("c", "b"), fn("d", "c"), fn("e", "d"))))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.errors()).anyMatch(error -> error.contains("chain of 5")));
    }

    @Test
    void aSlotHoldsAtMostFifteen() {
        List<WorkflowFunction> sixteen = IntStream.range(0, 16).mapToObj(i -> fn("f" + i)).toList();

        assertThatThrownBy(() -> WorkflowValidator.validate(sixteen))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.errors()).anyMatch(error -> error.contains("at most 15")));
    }
}
