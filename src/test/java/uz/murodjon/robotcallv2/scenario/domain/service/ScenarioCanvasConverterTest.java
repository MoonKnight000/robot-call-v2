package uz.murodjon.robotcallv2.scenario.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.scenario.application.dto.CanvasEdgeDto;
import uz.murodjon.robotcallv2.scenario.application.dto.CanvasGraphDto;
import uz.murodjon.robotcallv2.scenario.application.dto.CanvasNodeDto;
import uz.murodjon.robotcallv2.scenario.domain.entity.*;
import uz.murodjon.robotcallv2.scenario.domain.enums.BranchOperator;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioCanvasConverterTest {

    @Test
    @DisplayName("Convert ScenarioDefinition to CanvasGraphDto and round-trip back with high fidelity")
    void roundTripConversionPreservesScenarioDefinition() {
        StageDef greeting = new StageDef(
                "GREETING",
                "Salomlashish va shaxsni aniqlash",
                List.of("OFFER", "WRONG_PERSON"),
                List.of(),
                "FRIENDLY",
                null,
                List.of(new StageBranch("debtAmount", BranchOperator.LESS_OR_EQUAL, "0", "OFFER")),
                Map.of("OFFER", "Mijoz ha dedi", "WRONG_PERSON", "Mijoz adashibsiz dedi"),
                "Assalomu alaykum {{clientName}}, siz bilan gaplashyapmanmi?",
                List.of("identified"), null, null, null, null
        );

        StageDef offer = new StageDef(
                "OFFER",
                "Maxsus taklifni bayon qilish",
                List.of("END"),
                List.of(),
                "CONFIDENT",
                null,
                null,
                Map.of("END", "Rozilik bildirdi"),
                "Siz uchun 15% chegirma mavjud!",
                null, null, null, null, null
        );

        StageDef end = new StageDef("END", "Yakunlash", List.of(), List.of());
        StageDef wrongPerson = new StageDef("WRONG_PERSON", "Raqam yanglish", List.of(), List.of());

        List<GlobalNode> globals = List.of(
                new GlobalNode("Siz kimsiz?", "Men Robot Call kompaniyasining virtual assistentiman.")
        );

        ScenarioDefinition original = new ScenarioDefinition(
                List.of(greeting, offer, end, wrongPerson),
                List.of(new FactField("clientName", "string", true, "Mijoz ismi"),
                        new FactField("debtAmount", "number", true, "Qarz summasi")),
                List.of(),
                List.of(new OutcomeField("identified", "boolean", "Tasdiqlandi")),
                "Siz professional sotuvchisiz.",
                List.of("Hech qachon haqorat qilmang"),
                "Qo'ng'iroq yozib olinadi va avtomatlashtirilgan tizim tomonidan amalga oshirilmoqda.",
                null,
                null,
                globals,
                "Mijozga maxsus taklifni yetkazish", null, null
        );

        // 1. Convert to Canvas
        CanvasGraphDto canvas = ScenarioCanvasConverter.toCanvasGraph(10L, "special-offer", "Maxsus Taklif", 1, original);

        assertThat(canvas).isNotNull();
        assertThat(canvas.scenarioId()).isEqualTo(10L);
        assertThat(canvas.name()).isEqualTo("Maxsus Taklif");
        assertThat(canvas.valid()).isTrue();

        // Verify Nodes
        assertThat(canvas.nodes()).isNotEmpty();
        CanvasNodeDto startNode = canvas.nodes().stream().filter(n -> "start".equals(n.id())).findFirst().orElseThrow();
        assertThat(startNode.data().get("callPurpose")).isEqualTo("Mijozga maxsus taklifni yetkazish");

        CanvasNodeDto greetingNode = canvas.nodes().stream().filter(n -> "GREETING".equals(n.id())).findFirst().orElseThrow();
        assertThat(greetingNode.data().get("staticLine")).contains("{{clientName}}");
        assertThat(greetingNode.position().x()).isGreaterThan(0);

        CanvasNodeDto globalNode = canvas.nodes().stream().filter(n -> "global_node".equals(n.type())).findFirst().orElseThrow();
        assertThat(globalNode.data().get("question")).isEqualTo("Siz kimsiz?");
        assertThat(globalNode.position().x()).isLessThan(0); // positioned on left sidebar

        // Verify Edges
        assertThat(canvas.edges()).isNotEmpty();
        CanvasEdgeDto transitionEdge = canvas.edges().stream()
                .filter(e -> "GREETING".equals(e.source()) && "OFFER".equals(e.target()) && "transition".equals(e.type()))
                .findFirst().orElseThrow();
        assertThat(transitionEdge.label()).isEqualTo("Mijoz ha dedi");

        CanvasEdgeDto branchEdge = canvas.edges().stream()
                .filter(e -> "branch".equals(e.type()))
                .findFirst().orElseThrow();
        assertThat(branchEdge.target()).isEqualTo("OFFER");
        assertThat(branchEdge.data().get("fact")).isEqualTo("debtAmount");

        // 2. Round-trip back to ScenarioDefinition
        ScenarioDefinition reconstructed = ScenarioCanvasConverter.toScenarioDefinition(canvas, original);

        assertThat(reconstructed).isNotNull();
        assertThat(reconstructed.callPurpose()).isEqualTo(original.callPurpose());
        assertThat(reconstructed.rolePrompt()).isEqualTo(original.rolePrompt());
        assertThat(reconstructed.disclosureText()).isEqualTo(original.disclosureText());
        assertThat(reconstructed.stages()).hasSize(4);

        StageDef recGreeting = reconstructed.findStage("GREETING");
        assertThat(recGreeting).isNotNull();
        assertThat(recGreeting.allowedTransitions()).containsExactlyInAnyOrder("OFFER", "WRONG_PERSON");
        assertThat(recGreeting.transitionConditions()).containsEntry("OFFER", "Mijoz ha dedi");
        assertThat(recGreeting.staticLine()).isEqualTo(greeting.staticLine());
        assertThat(recGreeting.emotion()).isEqualTo("FRIENDLY");
        assertThat(recGreeting.branches()).hasSize(1);
        assertThat(recGreeting.branches().get(0).fact()).isEqualTo("debtAmount");

        assertThat(reconstructed.globalNodes()).hasSize(1);
        assertThat(reconstructed.globalNodes().get(0).question()).isEqualTo("Siz kimsiz?");
    }

    @Test
    @DisplayName("AutoLayout arranges stages in topological horizontal layers without overlapping")
    void autoLayoutPositionsStagesCorrectly() {
        StageDef s1 = new StageDef("STEP_1", "Birinchi qadam", List.of("STEP_2A", "STEP_2B"), List.of());
        StageDef s2a = new StageDef("STEP_2A", "Ikkinchi A", List.of("STEP_3"), List.of());
        StageDef s2b = new StageDef("STEP_2B", "Ikkinchi B", List.of("STEP_3"), List.of());
        StageDef s3 = new StageDef("STEP_3", "Uchinchi yakuniy", List.of(), List.of());

        ScenarioDefinition def = new ScenarioDefinition(List.of(s1, s2a, s2b, s3), List.of(), List.of(), List.of(),
                "Prompt", List.of(), "Disclosure");

        CanvasGraphDto canvas = ScenarioCanvasConverter.toCanvasGraph(1L, "demo", "Demo", 1, def);
        CanvasGraphDto laidOut = ScenarioCanvasConverter.autoLayout(canvas);

        CanvasNodeDto n1 = laidOut.nodes().stream().filter(n -> "STEP_1".equals(n.id())).findFirst().orElseThrow();
        CanvasNodeDto n2a = laidOut.nodes().stream().filter(n -> "STEP_2A".equals(n.id())).findFirst().orElseThrow();
        CanvasNodeDto n2b = laidOut.nodes().stream().filter(n -> "STEP_2B".equals(n.id())).findFirst().orElseThrow();
        CanvasNodeDto n3 = laidOut.nodes().stream().filter(n -> "STEP_3".equals(n.id())).findFirst().orElseThrow();

        // X coordinate must increase along the topological rank: STEP_1 < STEP_2A <= STEP_2B < STEP_3
        assertThat(n1.position().x()).isLessThan(n2a.position().x());
        assertThat(n2a.position().x()).isEqualTo(n2b.position().x());
        assertThat(n2a.position().x()).isLessThan(n3.position().x());

        // Siblings on the same rank STEP_2A and STEP_2B must have distinct Y coordinates
        assertThat(n2a.position().y()).isNotEqualTo(n2b.position().y());
    }
}
