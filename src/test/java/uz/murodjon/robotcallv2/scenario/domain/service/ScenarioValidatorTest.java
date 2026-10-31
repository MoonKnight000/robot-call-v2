package uz.murodjon.robotcallv2.scenario.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.scenario.domain.entity.*;
import uz.murodjon.robotcallv2.scenario.domain.enums.BranchOperator;
import uz.murodjon.robotcallv2.scenario.domain.enums.StageType;
import uz.murodjon.robotcallv2.scenario.domain.enums.TransferMode;

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
                "the start stage GREETING cannot have a staticLine — the agent's firstMessage opens the call");
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

    @Test
    void aFlowOfEveryNodeTypeIsValid() {
        ScenarioDefinition def = flow(
                new StageDef("GREETING", "Salom", List.of("SPLIT"), List.of()),
                StageDef.builder().id("SPLIT").type(StageType.LOGIC_SPLIT)
                        .branches(List.of(
                                new StageBranch("debtAmount", BranchOperator.GREATER, "0", "LOOKUP"),
                                new StageBranch(null, BranchOperator.ALWAYS, null, "BYE")))
                        .allowedTransitions(List.of("LOOKUP", "BYE")).build(),
                action("LOOKUP", StageType.FUNCTION, "IVR", new StageAction("lookupDebt", null, null, null, null, null, null, null, null, null)),
                action("IVR", StageType.PRESS_DIGIT, "SMS", new StageAction(null, null, null, "1w2#", null, null, null, null, null, null)),
                action("SMS", StageType.SMS, "EXTRACT", new StageAction(null, null, null, null, null, "Karta: {{card}}", null, null, null, null)),
                action("EXTRACT", StageType.EXTRACT_VARIABLE, "CODE", new StageAction(null, null, null, null, null, null, List.of("res"), null, null, null)),
                action("CODE", StageType.CODE, "MCP", new StageAction(null, null, null, null, null, null, null, "return 1;", null, null)),
                action("MCP", StageType.MCP, "BYE", new StageAction("crm.lookup", null, null, null, null, null, null, null, 7L, null)),
                StageDef.builder().id("HUMAN").type(StageType.CALL_TRANSFER).globalCondition("Mijoz operator so'raydi")
                        .action(new StageAction(null, "+998901234567", TransferMode.WARM, null, null, null, null, null, null, null))
                        .allowedTransitions(List.of("BYE")).build(),
                StageDef.builder().id("SALES").type(StageType.AGENT_TRANSFER)
                        .action(new StageAction(null, null, null, null, 42L, null, null, null, null, null)).build(),
                StageDef.builder().id("BYE").type(StageType.ENDING).staticLine("Xayr!").build(),
                StageDef.builder().id("TODO").type(StageType.NOTE).purpose("Keyin FAQ qo'shamiz").build());

        assertThat(ScenarioValidator.validate(def)).isEmpty();
    }

    @Test
    void anActionStageWithoutATransitionReturnsError() {
        ScenarioDefinition def = flow(
                new StageDef("GREETING", "Salom", List.of("SMS", "END"), List.of()),
                StageDef.builder().id("SMS").type(StageType.SMS)
                        .action(new StageAction(null, null, null, null, null, "Salom", null, null, null, null)).build(),
                new StageDef("END", "Yakunla", List.of(), List.of()));

        assertThat(ScenarioValidator.validate(def)).contains("SMS stage SMS needs a transition to pass the call on");
    }

    @Test
    void anActionStageWithoutItsSettingsReturnsError() {
        ScenarioDefinition def = flow(
                new StageDef("GREETING", "Salom", List.of("XFER", "IVR", "LOOKUP"), List.of()),
                StageDef.builder().id("XFER").type(StageType.CALL_TRANSFER)
                        .action(new StageAction(null, "operator", null, null, null, null, null, null, null, null)).build(),
                action("IVR", StageType.PRESS_DIGIT, "END", new StageAction(null, null, null, "1-2", null, null, null, null, null, null)),
                action("LOOKUP", StageType.FUNCTION, "END", new StageAction(" ", null, null, null, null, null, null, null, null, null)),
                new StageDef("END", "Yakunla", List.of(), List.of()));

        assertThat(ScenarioValidator.validate(def)).contains(
                "CALL_TRANSFER stage XFER needs action.transferTo as a phone number",
                "PRESS_DIGIT stage IVR needs action.digits of 0-9, *, # and w",
                "FUNCTION stage LOOKUP needs action.toolName");
    }

    @Test
    void aFunctionStageMayCallACompanyToolTheScenarioDoesNotDeclare() {
        ScenarioDefinition def = flow(
                new StageDef("GREETING", "Salom", List.of("LOOKUP"), List.of()),
                action("LOOKUP", StageType.FUNCTION, "END", new StageAction("crm_find_client", null, null, null, null, null, null, null, null, null)),
                new StageDef("END", "Yakunla", List.of(), List.of()));

        assertThat(ScenarioValidator.validate(def)).isEmpty();
    }

    @Test
    void theStartStageMustBeAConversation() {
        ScenarioDefinition def = flow(
                        new StageDef("GREETING", "Salom", List.of("END"), List.of()),
                        action("IVR", StageType.PRESS_DIGIT, "GREETING", new StageAction(null, null, null, "1", null, null, null, null, null, null)),
                        new StageDef("END", "Yakunla", List.of(), List.of()))
                .toBuilder().startStageId("IVR").build();

        assertThat(ScenarioValidator.validate(def)).contains(
                "the start stage IVR must be a CONVERSATION stage, not PRESS_DIGIT");
    }

    @Test
    void aLogicSplitWithoutAnElseBranchReturnsError() {
        ScenarioDefinition def = flow(
                new StageDef("GREETING", "Salom", List.of("SPLIT"), List.of()),
                StageDef.builder().id("SPLIT").type(StageType.LOGIC_SPLIT)
                        .branches(List.of(new StageBranch("debtAmount", BranchOperator.GREATER, "0", "END")))
                        .allowedTransitions(List.of("END")).build(),
                new StageDef("END", "Yakunla", List.of(), List.of()));

        assertThat(ScenarioValidator.validate(def)).contains(
                "LOGIC_SPLIT stage SPLIT needs branches ending with an ALWAYS branch");
    }

    @Test
    void anAlwaysBranchBeforeAnotherReturnsError() {
        ScenarioDefinition def = flow(
                new StageDef("GREETING", "Salom", List.of("SPLIT"), List.of()),
                StageDef.builder().id("SPLIT").type(StageType.LOGIC_SPLIT)
                        .branches(List.of(
                                new StageBranch(null, BranchOperator.ALWAYS, null, "END"),
                                new StageBranch(null, BranchOperator.ALWAYS, null, "END")))
                        .allowedTransitions(List.of("END")).build(),
                new StageDef("END", "Yakunla", List.of(), List.of()));

        assertThat(ScenarioValidator.validate(def)).contains("the ALWAYS branch of stage SPLIT must be its last");
    }

    @Test
    void endingsAndNotesCannotBeLeftOrEntered() {
        ScenarioDefinition def = flow(
                new StageDef("GREETING", "Salom", List.of("BYE", "TODO"), List.of()),
                StageDef.builder().id("BYE").type(StageType.ENDING).allowedTransitions(List.of("GREETING")).build(),
                StageDef.builder().id("TODO").type(StageType.NOTE).purpose("Eslatma").build(),
                new StageDef("END", "Yakunla", List.of(), List.of()));

        assertThat(ScenarioValidator.validate(def)).contains(
                "ENDING stage BYE cannot have transitions",
                "stage GREETING transitions to NOTE TODO");
    }

    @Test
    void theStartStageIsTheOneStartStageIdNames() {
        ScenarioDefinition def = flow(
                        new StageDef("END", "Yakunla", List.of(), List.of()),
                        StageDef.builder().id("GREETING").purpose("Salom").allowedTransitions(List.of("END"))
                                .staticLine("Salom!").build())
                .toBuilder().startStageId("GREETING").build();

        assertThat(def.findStartStage().id()).isEqualTo("GREETING");
        assertThat(ScenarioValidator.validate(def)).contains(
                "the start stage GREETING cannot have a staticLine — the agent's firstMessage opens the call");
    }

    @Test
    void anUnknownOrGlobalStartStageReturnsError() {
        ScenarioDefinition unknown = flow(new StageDef("END", "Yakunla", List.of(), List.of()))
                .toBuilder().startStageId("NOPE").build();
        ScenarioDefinition global = flow(
                        StageDef.builder().id("HUMAN").purpose("Operator").globalCondition("Operator so'raydi")
                                .allowedTransitions(List.of("END")).build(),
                        new StageDef("END", "Yakunla", List.of(), List.of()))
                .toBuilder().startStageId("HUMAN").build();

        assertThat(ScenarioValidator.validate(unknown)).contains("startStageId NOPE is not a stage");
        assertThat(ScenarioValidator.validate(global)).contains("the start stage HUMAN cannot be global");
    }

    private static ScenarioDefinition flow(StageDef... stages) {
        return ScenarioDefinition.builder()
                .stages(List.of(stages))
                .tools(List.of(new ToolDef("lookupDebt", "Qarzni topadi", List.of())))
                .outcomeSchema(List.of(new OutcomeField("res", "string", "Desc")))
                .rolePrompt("Prompt")
                .build();
    }

    private static StageDef action(String id, StageType type, String next, StageAction action) {
        return StageDef.builder().id(id).type(type).action(action).allowedTransitions(List.of(next)).build();
    }
}
