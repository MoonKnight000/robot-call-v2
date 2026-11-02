package uz.murodjon.robotcallv2.scenario.domain.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.scenario.domain.enums.StageType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioDefinitionSubflowsJsonTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void subflowsSurviveAJsonRoundTrip() throws Exception {
        StageDef block = StageDef.builder().id("collect").type(StageType.SUBFLOW)
                .action(new StageAction(null, null, null, null, null, null, null, null, null, null, "ask"))
                .allowedTransitions(List.of("done")).build();
        ScenarioDefinition original = ScenarioDefinition.builder()
                .stages(List.of(block, StageDef.builder().id("done").type(StageType.ENDING).build()))
                .subflows(List.of(new SubflowDef("ask", "Ask", "s1", List.of(
                        new StageDef("s1", "Ask the name", List.of(), List.of())))))
                .build();

        ScenarioDefinition read = json.readValue(json.writeValueAsString(original), ScenarioDefinition.class);

        assertThat(read.subflows()).hasSize(1);
        assertThat(read.findSubflow("ASK").title()).isEqualTo("Ask");
        assertThat(read.findSubflow("ask").stages()).extracting(StageDef::id).containsExactly("s1");
        assertThat(read.findStage("collect").action().subflowId()).isEqualTo("ask");
    }

    @Test
    void aScenarioWithoutSubflowsLeavesThemOutOfItsJson() throws Exception {
        ScenarioDefinition plain = ScenarioDefinition.builder()
                .stages(List.of(StageDef.builder().id("done").type(StageType.ENDING).build()))
                .build();

        assertThat(json.writeValueAsString(plain)).doesNotContain("subflows");
    }

    @Test
    void aStoredDefinitionWrittenBeforeSubflowsExistedStillReads() throws Exception {
        ScenarioDefinition read = json.readValue("{\"stages\":[{\"id\":\"done\",\"type\":\"ENDING\"}]}", ScenarioDefinition.class);

        assertThat(read.subflows()).isEmpty();
    }
}
