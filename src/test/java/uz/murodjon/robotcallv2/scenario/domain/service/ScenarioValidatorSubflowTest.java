package uz.murodjon.robotcallv2.scenario.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.scenario.domain.entity.*;
import uz.murodjon.robotcallv2.scenario.domain.enums.StageType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioValidatorSubflowTest {

    private static final SubflowDef ASK = new SubflowDef("ask", "Ask", null, List.of(
            stage("s1", "s2"),
            stage("s2")));

    @Test
    void aFlowThatEmbedsASubflowPasses() {
        ScenarioDefinition scenario = flow(stage("greet", "collect"), block("collect", "ask", "done"), ending("done"), ASK);

        assertThat(ScenarioValidator.validate(scenario)).isEmpty();
    }

    @Test
    void aSubflowNobodyEmbedsIsAllowed() {
        ScenarioDefinition scenario = flow(stage("greet", "done"), ending("done"), ASK);

        assertThat(ScenarioValidator.validate(scenario)).isEmpty();
    }

    @Test
    void aBlockWithoutASubflowIdIsRefused() {
        StageDef bare = StageDef.builder().id("collect").type(StageType.SUBFLOW).allowedTransitions(List.of("done")).build();

        List<String> errors = ScenarioValidator.validate(flow(stage("greet", "collect"), bare, ending("done"), ASK));

        assertThat(errors).contains("SUBFLOW stage collect needs action.subflowId");
    }

    @Test
    void aBlockEmbeddingAnUnknownSubflowIsRefused() {
        List<String> errors = ScenarioValidator.validate(
                flow(stage("greet", "collect"), block("collect", "ghost", "done"), ending("done"), ASK));

        assertThat(errors).contains("SUBFLOW stage collect embeds unknown subflow ghost");
    }

    @Test
    void aSubflowCannotEmbedAnotherSubflow() {
        SubflowDef nesting = new SubflowDef("outer", null, null, List.of(block("inner", "ask", "end"), stage("end")));

        List<String> errors = ScenarioValidator.validate(
                flow(stage("greet", "collect"), block("collect", "outer", "done"), ending("done"), ASK, nesting));

        assertThat(errors).contains("subflow outer cannot embed another subflow (stage inner)");
    }

    @Test
    void aSubflowThatCanNeitherLeaveNorEndIsRefused() {
        SubflowDef loop = new SubflowDef("loop", null, null, List.of(stage("a", "b"), stage("b", "a")));

        List<String> errors = ScenarioValidator.validate(
                flow(stage("greet", "collect"), block("collect", "loop", "done"), ending("done"), loop));

        assertThat(errors).anyMatch(e -> e.contains("subflow loop needs a stage with no outgoing transitions"));
    }

    @Test
    void aSubflowStageNobodyCanReachIsRefused() {
        SubflowDef orphan = new SubflowDef("orphan", null, null, List.of(stage("a"), stage("lost")));

        List<String> errors = ScenarioValidator.validate(
                flow(stage("greet", "collect"), block("collect", "orphan", "done"), ending("done"), orphan));

        assertThat(errors).contains("stage lost of subflow orphan is not reachable from its start");
    }

    @Test
    void aSubflowStageCannotBeGlobal() {
        StageDef global = StageDef.builder().id("help").globalCondition("asks for a human").build();
        SubflowDef withGlobal = new SubflowDef("withGlobal", null, null, List.of(stage("a", "help"), global));

        List<String> errors = ScenarioValidator.validate(
                flow(stage("greet", "collect"), block("collect", "withGlobal", "done"), ending("done"), withGlobal));

        assertThat(errors).contains("stage help of subflow withGlobal cannot be global");
    }

    @Test
    void duplicateSubflowIdsAreRefused() {
        List<String> errors = ScenarioValidator.validate(
                flow(stage("greet", "collect"), block("collect", "ask", "done"), ending("done"), ASK, ASK));

        assertThat(errors).contains("duplicate subflow id: ask");
    }

    @Test
    void aSubflowStartingTheFlowIsHeldToTheStartStageRules() {
        SubflowDef startsWithATool = new SubflowDef("tool", null, null, List.of(
                StageDef.builder().id("t").type(StageType.FUNCTION).allowedTransitions(List.of("t2"))
                        .action(new StageAction("lookupDebt", null, null, null, null, null, null, null, null, null, null)).build(),
                stage("t2")));
        ScenarioDefinition scenario = flow(block("collect", "tool", "done"), ending("done"), startsWithATool);

        assertThat(ScenarioValidator.validate(scenario))
                .contains("the start stage collect__t must be a CONVERSATION stage, not FUNCTION");
    }

    /** The stages, plus the subflows that follow them, on top of the tool and outcome every valid flow needs. */
    private static ScenarioDefinition flow(Object... parts) {
        List<StageDef> stages = new java.util.ArrayList<>();
        List<SubflowDef> subflows = new java.util.ArrayList<>();
        for (Object part : parts) {
            if (part instanceof StageDef stage) {
                stages.add(stage);
            } else {
                subflows.add((SubflowDef) part);
            }
        }
        return ScenarioDefinition.builder()
                .stages(stages)
                .subflows(subflows)
                .tools(List.of(new ToolDef("lookupDebt", "Qarzni topadi", List.of())))
                .outcomeSchema(List.of(new OutcomeField("res", "string", "Desc")))
                .rolePrompt("Prompt")
                .build();
    }

    private static StageDef stage(String id, String... next) {
        return StageDef.builder().id(id).allowedTransitions(List.of(next)).build();
    }

    private static StageDef ending(String id) {
        return StageDef.builder().id(id).type(StageType.ENDING).build();
    }

    private static StageDef block(String id, String subflowId, String next) {
        return StageDef.builder().id(id).type(StageType.SUBFLOW)
                .action(new StageAction(null, null, null, null, null, null, null, null, null, null, subflowId))
                .allowedTransitions(List.of(next)).build();
    }
}
