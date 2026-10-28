package uz.murodjon.robotcallv2.scenario.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.scenario.domain.entity.*;

import java.util.List;
import java.util.Map;

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

    @Test
    void aGlobalNodeWithoutAnAnswerReturnsError() {
        ScenarioDefinition def = ScenarioDefinition.builder()
                .stages(List.of(new StageDef("START", "Start", List.of(), List.of())))
                .factSchema(List.of())
                .tools(List.of())
                .outcomeSchema(List.of(new OutcomeField("res", "string", "Desc")))
                .rolePrompt("Prompt")
                .guardrails(List.of())
                .globalNodes(List.of(new GlobalNode("Raqamimni qayerdan oldingiz?", " ")))
                .build();

        List<String> errors = ScenarioValidator.validate(def);
        assertThat(errors).contains("a global node needs both 'question' and 'answer'");
    }

    @Test
    void aTransitionConditionAloneMakesItsTargetAllowed() {
        ScenarioDefinition def = new ScenarioDefinition(
                List.of(
                        StageDef.builder()
                                .id("ASK_TIME")
                                .purpose("Vaqti bormi so'rang")
                                .allowedTools(List.of())
                                .transitionConditions(Map.of("RESCHEDULE", "mijoz bandman desa"))
                                .build(),
                        new StageDef("RESCHEDULE", "Qachon qulay ekanini so'rang", List.of(), List.of())
                ),
                List.of(), List.of(),
                List.of(new OutcomeField("res", "string", "Desc")),
                "Prompt", List.of(), null);

        assertThat(def.stages().get(0).allowedTransitions()).containsExactly("RESCHEDULE");
        assertThat(ScenarioValidator.validate(def)).isEmpty();
    }

    @Test
    void aTransitionConditionOnAnUnknownStageReturnsError() {
        ScenarioDefinition def = new ScenarioDefinition(
                List.of(
                        StageDef.builder()
                                .id("ASK_TIME")
                                .purpose("Vaqti bormi so'rang")
                                .allowedTransitions(List.of("END"))
                                .allowedTools(List.of())
                                .transitionConditions(Map.of("RESCHEDULE", "mijoz bandman desa"))
                                .build(),
                        new StageDef("END", "Yakunla", List.of(), List.of())
                ),
                List.of(), List.of(),
                List.of(new OutcomeField("res", "string", "Desc")),
                "Prompt", List.of(), null);

        assertThat(ScenarioValidator.validate(def)).contains("stage ASK_TIME transitions to unknown stage RESCHEDULE");
    }

    @Test
    void aBlankTransitionConditionReturnsError() {
        ScenarioDefinition def = new ScenarioDefinition(
                List.of(
                        StageDef.builder()
                                .id("ASK_TIME")
                                .purpose("Vaqti bormi so'rang")
                                .allowedTransitions(List.of("END"))
                                .allowedTools(List.of())
                                .transitionConditions(Map.of("END", " "))
                                .build(),
                        new StageDef("END", "Yakunla", List.of(), List.of())
                ),
                List.of(), List.of(),
                List.of(new OutcomeField("res", "string", "Desc")),
                "Prompt", List.of(), null);

        List<String> errors = ScenarioValidator.validate(def);
        assertThat(errors).contains("transition condition of stage ASK_TIME for END must not be empty");
    }

    @Test
    void transitionConditionsOnAllowedStagesAreValid() {
        ScenarioDefinition def = new ScenarioDefinition(
                List.of(
                        StageDef.builder()
                                .id("ASK_TIME")
                                .purpose("Vaqti bormi so'rang")
                                .allowedTransitions(List.of("HAS_TIME", "RESCHEDULE"))
                                .allowedTools(List.of())
                                .transitionConditions(
                                        Map.of("HAS_TIME", "mijoz vaqtim bor desa", "RESCHEDULE", "mijoz bandman desa"))
                                .build(),
                        new StageDef("HAS_TIME", "Taklifni ayting", List.of(), List.of()),
                        new StageDef("RESCHEDULE", "Qachon qulay ekanini so'rang", List.of(), List.of())
                ),
                List.of(), List.of(),
                List.of(new OutcomeField("res", "string", "Desc")),
                "Prompt", List.of(), null);

        assertThat(ScenarioValidator.validate(def)).isEmpty();
    }

    @Test
    void theFirstStageCannotHaveAStaticLine() {
        ScenarioDefinition def = new ScenarioDefinition(
                List.of(
                        StageDef.builder()
                                .id("GREETING")
                                .purpose("Salom")
                                .allowedTransitions(List.of("END"))
                                .allowedTools(List.of())
                                .staticLine("Assalomu alaykum!")
                                .build(),
                        new StageDef("END", "Yakunla", List.of(), List.of())
                ),
                List.of(), List.of(),
                List.of(new OutcomeField("res", "string", "Desc")),
                "Prompt", List.of(), null);

        assertThat(ScenarioValidator.validate(def)).contains(
                "the first stage GREETING cannot have a staticLine — the agent's firstMessage opens the call");
    }

    @Test
    void aTerminalStageCannotRequireOutcomes() {
        ScenarioDefinition def = new ScenarioDefinition(
                List.of(
                        new StageDef("START", "Salom", List.of("END"), List.of()),
                        StageDef.builder()
                                .id("END")
                                .purpose("Yakunla")
                                .allowedTransitions(List.of())
                                .allowedTools(List.of())
                                .requiredOutcomes(List.of("promisedDate"))
                                .build()
                ),
                List.of(), List.of(),
                List.of(new OutcomeField("res", "string", "Desc")),
                "Prompt", List.of(), null);

        assertThat(ScenarioValidator.validate(def)).contains(
                "terminal stage END cannot have requiredOutcomes — the call never leaves it");
    }

    @Test
    void aStaticLineAndRequiredOutcomesOnALaterStageAreValid() {
        ScenarioDefinition def = new ScenarioDefinition(
                List.of(
                        new StageDef("START", "Salom", List.of("ASK_DATE"), List.of()),
                        StageDef.builder()
                                .id("ASK_DATE")
                                .purpose("Sanani so'rang")
                                .allowedTransitions(List.of("END"))
                                .allowedTools(List.of())
                                .staticLine("{{clientName}} aka, qachon to'lay olasiz?")
                                .requiredOutcomes(List.of("promisedDate", "reasonCode"))
                                .build(),
                        new StageDef("END", "Yakunla", List.of(), List.of())
                ),
                List.of(), List.of(),
                List.of(new OutcomeField("res", "string", "Desc")),
                "Prompt", List.of(), null);

        assertThat(ScenarioValidator.validate(def)).isEmpty();
    }
}
