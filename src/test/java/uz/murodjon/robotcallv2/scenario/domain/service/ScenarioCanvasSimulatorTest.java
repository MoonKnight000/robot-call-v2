package uz.murodjon.robotcallv2.scenario.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.scenario.application.dto.CanvasDryRunRequest;
import uz.murodjon.robotcallv2.scenario.application.dto.CanvasDryRunResponse;
import uz.murodjon.robotcallv2.scenario.domain.entity.*;
import uz.murodjon.robotcallv2.scenario.domain.enums.BranchOperator;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioCanvasSimulatorTest {

    private ScenarioDefinition createTestScenario() {
        StageDef greeting = new StageDef(
                "GREETING",
                "Salomlashish",
                List.of("NEGOTIATE", "CANCEL"),
                List.of(),
                "POLITE",
                null,
                List.of(new StageBranch("paidInFull", BranchOperator.EQUALS, "true", "SUCCESS_END")),
                Map.of("NEGOTIATE", "Mijoz rozi bo'ldi", "CANCEL", "Mijoz rad etdi"),
                "Assalomu alaykum {{clientName}}, sizda {{debtAmount | \"0\"}} so'm to'lov mavjud.",
                List.of("contacted"), null, null, null, null
        );

        StageDef negotiate = new StageDef(
                "NEGOTIATE",
                "To'lov sanasini kelishish",
                List.of("SUCCESS_END"),
                List.of(),
                "PERSUASIVE",
                null,
                null,
                Map.of("SUCCESS_END", "Sana kelishildi"),
                "Qaysi sanada to'lovni amalga oshirasiz?",
                List.of("paymentDate"), null, null, null, null
        );

        StageDef successEnd = new StageDef(
                "SUCCESS_END",
                "Muvaffaqiyatli yakunlash",
                List.of(),
                List.of(),
                "HAPPY",
                null,
                null,
                null,
                "Rahmat, sizga SMS yuborildi. Xayr!",
                null, null, null, null, null
        );

        StageDef cancel = new StageDef(
                "CANCEL",
                "Rad etilganda yakunlash",
                List.of(),
                List.of(),
                "NEUTRAL",
                null,
                null,
                null,
                "Tushunarli, ma'lumot qayd etildi.",
                null, null, null, null, null
        );

        List<GlobalNode> globalNodes = List.of(
                new GlobalNode("Siz kimsiz?", "Men Robot Call kompaniyasining aqlli ovozli assistentiman.")
        );

        return new ScenarioDefinition(
                List.of(greeting, negotiate, successEnd, cancel),
                List.of(),
                List.of(),
                List.of(),
                "Test prompt",
                List.of(),
                "Disclosure",
                null,
                null,
                globalNodes,
                "Qarzdorlik eslatmasi", null, null
        );
    }

    @Test
    @DisplayName("Initial entry renders static line with injected fact placeholders")
    void initialEntryRendersStaticLine() {
        ScenarioDefinition def = createTestScenario();
        CanvasDryRunRequest req = new CanvasDryRunRequest(
                1L,
                "GREETING",
                "",
                Map.of("clientName", "Azizbek", "debtAmount", 450000),
                Map.of(),
                null
        );

        CanvasDryRunResponse res = ScenarioCanvasSimulator.step(def, req);

        assertThat(res).isNotNull();
        assertThat(res.previousNodeId()).isEqualTo("GREETING");
        assertThat(res.nextNodeId()).isEqualTo("GREETING");
        assertThat(res.botResponse()).contains("Azizbek");
        assertThat(res.botResponse()).contains("450000 so'm to'lov mavjud");
        assertThat(res.terminal()).isFalse();
        assertThat(res.availableTransitions()).containsExactlyInAnyOrder("NEGOTIATE", "CANCEL");
    }

    @Test
    @DisplayName("Caller FAQ interrupt matches GlobalNode, answers immediately without moving stage")
    void globalInterruptAnswersWithoutMovingStage() {
        ScenarioDefinition def = createTestScenario();
        CanvasDryRunRequest req = new CanvasDryRunRequest(
                1L,
                "GREETING",
                "Kechirasiz siz kimsiz o'zi?",
                Map.of("clientName", "Azizbek"),
                Map.of(),
                null
        );

        CanvasDryRunResponse res = ScenarioCanvasSimulator.step(def, req);

        assertThat(res.previousNodeId()).isEqualTo("GREETING");
        assertThat(res.nextNodeId()).isEqualTo("GREETING");
        assertThat(res.botResponse()).isEqualTo("Men Robot Call kompaniyasining aqlli ovozli assistentiman.");
        assertThat(res.transitionReason()).contains("Global FAQ interrupt matched");
        assertThat(res.activeEdgeId()).isNull();
        assertThat(res.terminal()).isFalse();
    }

    @Test
    @DisplayName("Deterministic branch rule matches fact and jumps directly to target stage")
    void branchEvaluatesFactAndJumpsDirectly() {
        ScenarioDefinition def = createTestScenario();
        // paidInFull == true triggers branch from GREETING to SUCCESS_END
        CanvasDryRunRequest req = new CanvasDryRunRequest(
                1L,
                "GREETING",
                "Eshitaman",
                Map.of("clientName", "Azizbek", "paidInFull", "true"),
                Map.of(),
                null
        );

        CanvasDryRunResponse res = ScenarioCanvasSimulator.step(def, req);

        assertThat(res.previousNodeId()).isEqualTo("GREETING");
        assertThat(res.nextNodeId()).isEqualTo("SUCCESS_END");
        assertThat(res.activeEdgeId()).contains("e_branch_GREETING_SUCCESS_END");
        assertThat(res.botResponse()).isEqualTo("Rahmat, sizga SMS yuborildi. Xayr!");
        assertThat(res.terminal()).isTrue();
    }

    @Test
    @DisplayName("Normal user turn transitions to next stage and renders target stage prompt")
    void callerTurnTransitionsToNextStage() {
        ScenarioDefinition def = createTestScenario();
        CanvasDryRunRequest req = new CanvasDryRunRequest(
                1L,
                "GREETING",
                "Ha to'lov qilishga rozi bo'ldim, qachon to'lasam bo'ladi?",
                Map.of("clientName", "Azizbek", "paidInFull", "false"),
                Map.of(),
                null
        );

        CanvasDryRunResponse res = ScenarioCanvasSimulator.step(def, req);

        assertThat(res.previousNodeId()).isEqualTo("GREETING");
        assertThat(res.nextNodeId()).isEqualTo("NEGOTIATE");
        assertThat(res.activeEdgeId()).isEqualTo("e_GREETING_to_NEGOTIATE");
        assertThat(res.botResponse()).isEqualTo("Qaysi sanada to'lovni amalga oshirasiz?");
        assertThat(res.terminal()).isFalse();
        assertThat(res.updatedOutcomes()).containsKey("contacted");
    }

    @Test
    @DisplayName("Entering terminal stage marks terminal as true")
    void terminalStageMarksTerminalTrue() {
        ScenarioDefinition def = createTestScenario();
        CanvasDryRunRequest req = new CanvasDryRunRequest(
                1L,
                "SUCCESS_END",
                "Rahmat, sog' bo'ling",
                Map.of(),
                Map.of(),
                null
        );

        CanvasDryRunResponse res = ScenarioCanvasSimulator.step(def, req);

        assertThat(res.previousNodeId()).isEqualTo("SUCCESS_END");
        assertThat(res.nextNodeId()).isEqualTo("SUCCESS_END");
        assertThat(res.terminal()).isTrue();
        assertThat(res.availableTransitions()).isEmpty();
    }
}
