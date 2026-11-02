package uz.murodjon.robotcallv2.scenario.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.scenario.domain.entity.*;
import uz.murodjon.robotcallv2.scenario.domain.enums.BranchOperator;
import uz.murodjon.robotcallv2.scenario.domain.enums.StageType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SubflowExpanderTest {

    /** ask: one question, then a stage with nowhere to go — the way out. */
    private static final SubflowDef ASK = new SubflowDef("ask", "Ask", null, List.of(
            stage("s1", "s2"),
            stage("s2")));

    @Test
    void aScenarioWithoutSubflowStagesIsReturnedAsItIs() {
        ScenarioDefinition plain = flow(null, stage("greet", "done"), stage("done"));

        assertThat(SubflowExpander.expand(plain)).isSameAs(plain);
    }

    @Test
    void theSubflowTakesTheBlocksPlaceAndTheCallLeavesToTheBlocksNextStage() {
        ScenarioDefinition scenario = flow(null, stage("greet", "collect"), block("collect", "ask", "done"), ending("done"))
                .toBuilder().subflows(List.of(ASK)).build();

        ScenarioDefinition flat = SubflowExpander.expand(scenario);

        assertThat(ids(flat)).containsExactly("greet", "collect__s1", "collect__s2", "done");
        assertThat(flat.subflows()).isEmpty();
        assertThat(transitions(flat, "greet")).containsExactly("collect__s1");
        assertThat(transitions(flat, "collect__s1")).containsExactly("collect__s2");
        assertThat(transitions(flat, "collect__s2")).containsExactly("done");
        assertThat(flat.stages()).noneMatch(s -> s.type() == StageType.SUBFLOW);
    }

    @Test
    void oneSubflowEmbeddedTwiceGetsAPrefixPerBlock() {
        ScenarioDefinition scenario = flow(null,
                stage("greet", "first"), block("first", "ask", "second"), block("second", "ask", "done"), ending("done"))
                .toBuilder().subflows(List.of(ASK)).build();

        ScenarioDefinition flat = SubflowExpander.expand(scenario);

        assertThat(ids(flat)).containsExactly("greet", "first__s1", "first__s2", "second__s1", "second__s2", "done");
        assertThat(transitions(flat, "first__s2")).containsExactly("second__s1");
        assertThat(transitions(flat, "second__s2")).containsExactly("done");
    }

    @Test
    void anEndingInsideTheSubflowKeepsEndingTheCall() {
        SubflowDef refuse = new SubflowDef("refuse", null, null, List.of(
                stage("ask", "bye"), ending("bye")));
        ScenarioDefinition scenario = flow(null, stage("greet", "gate"), block("gate", "refuse", "done"), ending("done"))
                .toBuilder().subflows(List.of(refuse)).build();

        ScenarioDefinition flat = SubflowExpander.expand(scenario);

        assertThat(transitions(flat, "gate__bye")).isEmpty();
        assertThat(transitions(flat, "gate__ask")).containsExactly("gate__bye");
    }

    @Test
    void conditionsAndBranchesPointingAtTheBlockFollowItInside() {
        StageDef greet = StageDef.builder().id("greet").allowedTransitions(List.of("collect"))
                .transitionConditions(Map.of("collect", "the client has time"))
                .branches(List.of(new StageBranch("known", BranchOperator.ALWAYS, null, "collect")))
                .build();
        ScenarioDefinition scenario = flow(null, greet, block("collect", "ask", "done"), ending("done"))
                .toBuilder().subflows(List.of(ASK)).build();

        StageDef flatGreet = SubflowExpander.expand(scenario).findStage("greet");

        assertThat(flatGreet.allowedTransitions()).containsExactly("collect__s1");
        assertThat(flatGreet.transitionConditions()).containsOnlyKeys("collect__s1");
        assertThat(flatGreet.branches()).extracting(StageBranch::goTo).containsExactly("collect__s1");
    }

    @Test
    void theWayOutKeepsTheBlocksConditions() {
        StageDef collect = StageDef.builder().id("collect").type(StageType.SUBFLOW).action(embeds("ask"))
                .transitionConditions(Map.of("done", "the answer was given")).build();
        ScenarioDefinition scenario = flow(null, stage("greet", "collect"), collect, ending("done"))
                .toBuilder().subflows(List.of(ASK)).build();

        StageDef exit = SubflowExpander.expand(scenario).findStage("collect__s2");

        assertThat(exit.allowedTransitions()).containsExactly("done");
        assertThat(exit.transitionConditions()).containsEntry("done", "the answer was given");
    }

    @Test
    void aBlockThatLeadsIntoAnotherBlockLeavesIntoItsStart() {
        SubflowDef confirm = new SubflowDef("confirm", null, null, List.of(stage("c1")));
        ScenarioDefinition scenario = flow(null,
                stage("greet", "collect"), block("collect", "ask", "check"), block("check", "confirm", "done"), ending("done"))
                .toBuilder().subflows(List.of(ASK, confirm)).build();

        ScenarioDefinition flat = SubflowExpander.expand(scenario);

        assertThat(transitions(flat, "collect__s2")).containsExactly("check__c1");
        assertThat(transitions(flat, "check__c1")).containsExactly("done");
    }

    @Test
    void aFlowOpeningOnABlockOpensOnTheSubflowsStart() {
        ScenarioDefinition scenario = flow("collect", block("collect", "ask", "done"), ending("done"))
                .toBuilder().subflows(List.of(ASK)).build();

        assertThat(SubflowExpander.expand(scenario).startStageId()).isEqualTo("collect__s1");
    }

    @Test
    void theSubflowsStartIsFirstEvenWhenItIsNotListedFirst() {
        SubflowDef lateStart = new SubflowDef("late", null, "b", List.of(stage("a"), stage("b", "a")));
        ScenarioDefinition scenario = flow(null, block("collect", "late", "done"), ending("done"))
                .toBuilder().subflows(List.of(lateStart)).build();

        ScenarioDefinition flat = SubflowExpander.expand(scenario);

        // A flow with no startStageId opens on its first stage, which is therefore the subflow's start.
        assertThat(ids(flat).get(0)).isEqualTo("collect__b");
        assertThat(flat.findStartStage().id()).isEqualTo("collect__b");
    }

    @Test
    void aBlockNamingNoKnownSubflowIsLeftForTheValidator() {
        ScenarioDefinition scenario = flow(null, stage("greet", "ghost"), block("ghost", "nope", "done"), ending("done"));

        assertThat(SubflowExpander.expand(scenario)).isSameAs(scenario);
    }

    private static ScenarioDefinition flow(String startStageId, StageDef... stages) {
        return ScenarioDefinition.builder().stages(List.of(stages)).startStageId(startStageId).build();
    }

    private static StageDef stage(String id, String... next) {
        return StageDef.builder().id(id).allowedTransitions(List.of(next)).build();
    }

    private static StageDef ending(String id) {
        return StageDef.builder().id(id).type(StageType.ENDING).build();
    }

    private static StageDef block(String id, String subflowId, String next) {
        return StageDef.builder().id(id).type(StageType.SUBFLOW).action(embeds(subflowId))
                .allowedTransitions(List.of(next)).build();
    }

    private static StageAction embeds(String subflowId) {
        return new StageAction(null, null, null, null, null, null, null, null, null, null, subflowId);
    }

    private static List<String> ids(ScenarioDefinition flow) {
        return flow.stages().stream().map(StageDef::id).toList();
    }

    private static List<String> transitions(ScenarioDefinition flow, String id) {
        return flow.findStage(id).allowedTransitions();
    }
}
