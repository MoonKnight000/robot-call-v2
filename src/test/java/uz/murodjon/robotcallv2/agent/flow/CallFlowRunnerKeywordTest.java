package uz.murodjon.robotcallv2.agent.flow;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlowNode;
import uz.murodjon.robotcallv2.callflow.domain.enums.CallFlowNodeType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CallFlowRunnerKeywordTest {

    private static final CallFlowNode MENU = new CallFlowNode(CallFlowNodeType.MENU, Map.of("uz-UZ", "x"), null,
            Map.of("1", "sales", "2", "payments"), null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, Map.of("1", List.of("uy", "kvartira"), "2", List.of("to'lov", "оплат")));

    /** Matching is by substring, so a Russian keyword is written as its stem to catch every case ending. */
    @Test
    void aSpokenKeywordPicksItsOption() {
        assertThat(CallFlowRunner.matchKeyword(MENU, "Menga kvartira kerak edi")).isEqualTo("1");
        assertThat(CallFlowRunner.matchKeyword(MENU, "Хочу узнать про оплату")).isEqualTo("2");
    }

    @Test
    void everyWayOfTypingTheApostropheIsOne() {
        assertThat(CallFlowRunner.matchKeyword(MENU, "to‘lov qilmoqchiman")).isEqualTo("2");
        assertThat(CallFlowRunner.matchKeyword(MENU, "TOʻLOV haqida")).isEqualTo("2");
    }

    @Test
    void nothingMatchesNothing() {
        assertThat(CallFlowRunner.matchKeyword(MENU, "salom")).isNull();
    }
}
