package uz.murodjon.robotcallv2.scenario.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.scenario.domain.entity.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioValidatorTest {

    @Test
    void nullDefinitionReturnsError() {
        List<String> errors = ScenarioValidator.validate(null);
        assertThat(errors).contains("definition must not be null");
    }

    @Test
    void emptyStagesReturnsError() {
        ScenarioDefinition def = new ScenarioDefinition(
                List.of(), List.of(), List.of(),
                List.of(new OutcomeField("result", "string", "Outcome description")),
                "Prompt", List.of(), null
        );

        List<String> errors = ScenarioValidator.validate(def);
        assertThat(errors).contains("stages must not be empty");
    }

    @Test
    void duplicateStageIdsReturnsError() {
        ScenarioDefinition def = new ScenarioDefinition(
                List.of(
                        new StageDef("GREETING", "Desc 1", List.of("END"), List.of()),
                        new StageDef("GREETING", "Desc 2", List.of(), List.of()),
                        new StageDef("END", "End stage", List.of(), List.of())
                ),
                List.of(), List.of(),
                List.of(new OutcomeField("result", "string", "Desc")),
                "Prompt", List.of(), null
        );

        List<String> errors = ScenarioValidator.validate(def);
        assertThat(errors).anyMatch(e -> e.contains("duplicate stage id: GREETING"));
    }

    @Test
    void unknownTransitionTargetReturnsError() {
        ScenarioDefinition def = new ScenarioDefinition(
                List.of(
                        new StageDef("START", "Start", List.of("NON_EXISTENT_STAGE"), List.of()),
                        new StageDef("END", "End", List.of(), List.of())
                ),
                List.of(), List.of(),
                List.of(new OutcomeField("res", "string", "Desc")),
                "Prompt", List.of(), null
        );

        List<String> errors = ScenarioValidator.validate(def);
        assertThat(errors).anyMatch(e -> e.contains("transitions to unknown stage NON_EXISTENT_STAGE"));
    }

    @Test
    void deadlockWithoutTerminalStageReturnsError() {
        ScenarioDefinition def = new ScenarioDefinition(
                List.of(
                        new StageDef("STAGE_A", "A", List.of("STAGE_B"), List.of()),
                        new StageDef("STAGE_B", "B", List.of("STAGE_A"), List.of())
                ),
                List.of(), List.of(),
                List.of(new OutcomeField("res", "string", "Desc")),
                "Prompt", List.of(), null
        );

        List<String> errors = ScenarioValidator.validate(def);
        assertThat(errors).anyMatch(e -> e.contains("no terminal stage"));
    }

    @Test
    void unreachableTerminalStageReturnsDeadlockError() {
        ScenarioDefinition def = new ScenarioDefinition(
                List.of(
                        new StageDef("START", "Start", List.of("LOOP_A"), List.of()),
                        new StageDef("LOOP_A", "Loop A", List.of("LOOP_B"), List.of()),
                        new StageDef("LOOP_B", "Loop B", List.of("LOOP_A"), List.of()),
                        new StageDef("ISOLATED_END", "End", List.of(), List.of())
                ),
                List.of(), List.of(),
                List.of(new OutcomeField("res", "string", "Desc")),
                "Prompt", List.of(), null
        );

        List<String> errors = ScenarioValidator.validate(def);
        assertThat(errors).anyMatch(e -> e.contains("cannot reach any terminal stage (deadlock)"));
    }

    @Test
    void reservedToolNameReturnsError() {
        ScenarioDefinition def = new ScenarioDefinition(
                List.of(new StageDef("END", "End", List.of(), List.of())),
                List.of(),
                List.of(new ToolDef("endCall", "End tool", List.of())),
                List.of(new OutcomeField("res", "string", "Desc")),
                "Prompt", List.of(), null
        );

        List<String> errors = ScenarioValidator.validate(def);
        assertThat(errors).anyMatch(e -> e.contains("reserved for the fixed universal tool set"));
    }

    @Test
    void emptyOutcomeSchemaReturnsError() {
        ScenarioDefinition def = new ScenarioDefinition(
                List.of(new StageDef("END", "End", List.of(), List.of())),
                List.of(), List.of(), List.of(), // empty outcome
                "Prompt", List.of(), null
        );

        List<String> errors = ScenarioValidator.validate(def);
        assertThat(errors).anyMatch(e -> e.contains("outcomeSchema must not be empty"));
    }

    @Test
    void validScenarioDefinitionPassesValidation() {
        ScenarioDefinition validDef = new ScenarioDefinition(
                List.of(
                        new StageDef("START", "Salomlashish", List.of("OFFER", "END"), List.of()),
                        new StageDef("OFFER", "Taklif qilish", List.of("END"), List.of("customTool")),
                        new StageDef("END", "Yakunlash", List.of(), List.of())
                ),
                List.of(new FactField("name", "string", true)),
                List.of(new ToolDef("customTool", "Custom action", List.of(new ToolParamDef("amount", "number", true, null)))),
                List.of(new OutcomeField("interested", "boolean", "Mijoz qiziqdimi")),
                "System prompt",
                List.of("Rule 1"),
                "Bu qo'ng'iroq avtomatik tizim orqali amalga oshirilmoqda va yozib olinmoqda"
        );

        List<String> errors = ScenarioValidator.validate(validDef);
        assertThat(errors).isEmpty();
    }
}
