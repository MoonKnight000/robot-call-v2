package uz.murodjon.robotcallv2.aiagent.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.scenario.domain.entity.GlobalNode;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageBranch;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.scenario.domain.enums.BranchOperator;
import uz.murodjon.robotcallv2.scenario.domain.enums.StageType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FlowPromptWriterTest {

    /** Listed out of order: the prompt walks it from the start stage. */
    private static final ScenarioDefinition FLOW = ScenarioDefinition.builder()
            .rolePrompt("Siz Uysot menejerisiz.")
            .callPurpose("Saytdan kelgan lid bilan gaplashish.")
            .startStageId("GREETING")
            .stages(List.of(
                    StageDef.builder().id("BYE").title("Xayrlashish").type(StageType.ENDING).build(),
                    StageDef.builder().id("CHECK").title("Lid bormi?").type(StageType.LOGIC_SPLIT)
                            .branches(List.of(
                                    new StageBranch("leadId", BranchOperator.EXISTS, null, "BYE"),
                                    new StageBranch(null, BranchOperator.ALWAYS, null, "GREETING")))
                            .build(),
                    StageDef.builder().id("GREETING").title("Salomlashish").purpose("Salom bering.")
                            .transitionConditions(Map.of("CHECK", "Mijoz qiziqsa"))
                            .build(),
                    StageDef.builder().id("MEMO").type(StageType.NOTE).purpose("canvas note").build()))
            .globalNodes(List.of(new GlobalNode("Siz kimsiz?", "Uysot menejeriman.")))
            .guardrails(List.of("Narxni o'zingizdan aytmang."))
            .build();

    @Test
    void theStagesAreWrittenInTheOrderACallReachesThem() {
        String prompt = FlowPromptWriter.writePrompt(FLOW, null);

        assertThat(prompt).startsWith("Siz Uysot menejerisiz.");
        assertThat(prompt.indexOf("### 1. Salomlashish")).isPositive();
        assertThat(prompt.indexOf("### 2. Lid bormi?")).isGreaterThan(prompt.indexOf("### 1. Salomlashish"));
        assertThat(prompt.indexOf("### 3. Xayrlashish")).isGreaterThan(prompt.indexOf("### 2. Lid bormi?"));
        assertThat(prompt).doesNotContain("canvas note");
    }

    @Test
    void everyEdgeSaysWhenItIsTaken() {
        String prompt = FlowPromptWriter.writePrompt(FLOW, null);

        assertThat(prompt).contains("- Mijoz qiziqsa → \"Lid bormi?\"");
        assertThat(prompt).contains("- Agar leadId ma'lum bo'lsa → \"Xayrlashish\"");
        assertThat(prompt).contains("- Aks holda → \"Salomlashish\"");
        assertThat(prompt).contains("qo'ng'iroqni yakunlang");
    }

    @Test
    void globalQuestionsAndRulesFollowTheStages() {
        String prompt = FlowPromptWriter.writePrompt(FLOW, null);

        assertThat(prompt).contains("- \"Siz kimsiz?\" → Uysot menejeriman.");
        assertThat(prompt).contains("## Qoidalar\n- Narxni o'zingizdan aytmang.");
    }

    @Test
    void theAgentsOwnPromptStandsInForAMissingRolePrompt() {
        ScenarioDefinition noRole = FLOW.toBuilder().rolePrompt(null).build();

        assertThat(FlowPromptWriter.writePrompt(noRole, "Siz yordamchisiz.")).startsWith("Siz yordamchisiz.");
    }
}
