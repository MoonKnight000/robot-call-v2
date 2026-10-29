package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.scenario.domain.entity.OutcomeField;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageAction;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageBranch;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.scenario.domain.entity.ToolDef;
import uz.murodjon.robotcallv2.scenario.domain.enums.BranchOperator;
import uz.murodjon.robotcallv2.scenario.domain.enums.StageType;
import uz.murodjon.robotcallv2.scenario.domain.enums.TransitionMode;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The flow stages that run without the model, as {@link DialogTools#transitionTo} enters them
 * on both pipelines.
 */
class StageEntryTest {

    private final List<String> tones = new ArrayList<>();
    private final List<String> texts = new ArrayList<>();

    @Test
    void stagesThatNeedNoWordsRunAndPassTheCallOn() {
        DialogSession session = session(flow(
                new StageDef("GREETING", "Salom", List.of("SPLIT"), List.of()),
                StageDef.builder().id("SPLIT").type(StageType.LOGIC_SPLIT)
                        .branches(List.of(
                                new StageBranch("debtAmount", BranchOperator.GREATER, "0", "IVR"),
                                new StageBranch(null, BranchOperator.ALWAYS, null, "BYE")))
                        .allowedTransitions(List.of("IVR", "BYE")).build(),
                action("IVR", StageType.PRESS_DIGIT, "SMS", new StageAction(null, null, null, "12#", null, null, null, null, null)),
                action("SMS", StageType.SMS, "NOTICE", new StageAction(null, null, null, null, null, "{{clientName}}, karta: 8600", null, null, null)),
                new StageDef("NOTICE", "Qarzni ayting", List.of("BYE"), List.of()),
                StageDef.builder().id("BYE").type(StageType.ENDING).staticLine("Xayr!").build()));

        String result = new DialogTools(session).transitionTo(null, "SPLIT");

        assertThat(session.state()).isEqualTo("NOTICE");
        assertThat(tones).containsExactly("12#");
        assertThat(texts).containsExactly("Aziz, karta: 8600");
        assertThat(result).contains("Moved to stage NOTICE").contains("automatically");
        assertThat(session.isEnded()).isFalse();
    }

    @Test
    void anEndingTheModelMovedToClosesTheCall() {
        DialogSession session = session(flow(
                new StageDef("GREETING", "Salom", List.of("BYE"), List.of()),
                StageDef.builder().id("BYE").type(StageType.ENDING).staticLine("Xayr!").build()));

        new DialogTools(session).transitionTo(null, "BYE");

        assertThat(session.isEnded()).isTrue();
    }

    @Test
    void anEndingReachedThroughAnotherStageAsksTheModelToSayItsLine() {
        DialogSession session = session(flow(
                new StageDef("GREETING", "Salom", List.of("SMS"), List.of()),
                action("SMS", StageType.SMS, "BYE", new StageAction(null, null, null, null, null, "Rahmat", null, null, null)),
                StageDef.builder().id("BYE").type(StageType.ENDING).staticLine("Xayr, {{clientName}}!").build()));

        String result = new DialogTools(session).transitionTo(null, "SMS");

        assertThat(session.isEnded()).isFalse();
        assertThat(session.state()).isEqualTo("BYE");
        assertThat(result).contains("say EXACTLY this: «Xayr, Aziz!»").contains("endCall");
    }

    @Test
    void aCallTransferStageHandsTheCallToItsNumber() {
        DialogSession session = session(flow(
                new StageDef("GREETING", "Salom", List.of("HUMAN"), List.of()),
                StageDef.builder().id("HUMAN").type(StageType.CALL_TRANSFER)
                        .action(new StageAction(null, "+998712000000", null, null, null, null, null, null, null)).build()));

        new DialogTools(session).transitionTo(null, "HUMAN");

        assertThat(session.isEnded()).isTrue();
        assertThat(session.disposition()).isEqualTo(Disposition.TRANSFERRED);
        assertThat(StageEntry.findTransferTarget(session.scenario(), session.state())).isEqualTo("+998712000000");
    }

    @Test
    void aGlobalStageIsReachableFromAnywhereEvenWithAnOutcomeOutstanding() {
        DialogSession session = session(flow(
                StageDef.builder().id("GREETING").purpose("Salom").allowedTransitions(List.of("ASK"))
                        .allowedTools(List.of()).build(),
                StageDef.builder().id("ASK").purpose("Sanani so'rang").allowedTransitions(List.of("END"))
                        .requiredOutcomes(List.of("res")).build(),
                StageDef.builder().id("HUMAN").purpose("Operatorga ulang").globalCondition("Mijoz operator so'raydi")
                        .allowedTransitions(List.of("END")).build(),
                new StageDef("END", "Yakunla", List.of(), List.of())));
        session.setState("ASK");

        String result = new DialogTools(session).transitionTo(null, "HUMAN");

        assertThat(result).doesNotStartWith("ERROR");
        assertThat(session.state()).isEqualTo("HUMAN");
        assertThat(SystemPromptFactory.describeReachableStages(session.scenario(), session.scenario().findStage("ASK")))
                .contains("HUMAN (at any time, if: Mijoz operator so'raydi)");
    }

    @Test
    void aFlexFlowLetsTheModelJumpToAnyConversationStage() {
        ScenarioDefinition flex = flow(
                new StageDef("GREETING", "Salom", List.of("ASK"), List.of()),
                new StageDef("ASK", "Sanani so'rang", List.of("CONFIRM"), List.of()),
                new StageDef("CONFIRM", "Tasdiqlang", List.of("END"), List.of()),
                new StageDef("END", "Yakunla", List.of(), List.of()))
                .toBuilder().transitionMode(TransitionMode.FLEX).build();
        DialogSession session = session(flex);

        new DialogTools(session).transitionTo(null, "CONFIRM");

        assertThat(session.state()).isEqualTo("CONFIRM");
    }

    @Test
    void aCodeStageRecordsWhatItsScriptReturnsAndBranchesOnIt() {
        StageScriptRunner scripts = mock(StageScriptRunner.class);
        when(scripts.run(anyString(), eq("return {segment: 'vip'};"), any())).thenReturn(Map.of("segment", "vip"));
        DialogSession session = session(flow(
                new StageDef("GREETING", "Salom", List.of("SCORE"), List.of()),
                StageDef.builder().id("SCORE").type(StageType.CODE)
                        .action(new StageAction(null, null, null, null, null, null, null, "return {segment: 'vip'};", null))
                        .branches(List.of(new StageBranch("segment", BranchOperator.EQUALS, "vip", "VIP")))
                        .allowedTransitions(List.of("VIP", "END")).build(),
                new StageDef("VIP", "VIP taklif", List.of("END"), List.of()),
                new StageDef("END", "Yakunla", List.of(), List.of())));

        new DialogTools(session, scripts).transitionTo(null, "SCORE");

        assertThat(session.outcome()).containsEntry("segment", "vip");
        assertThat(session.state()).isEqualTo("VIP");
    }

    @Test
    void anAgentTransferThatCannotHandOffTakesItsFallback() {
        DialogSession session = session(flow(
                new StageDef("GREETING", "Salom", List.of("SALES"), List.of()),
                StageDef.builder().id("SALES").type(StageType.AGENT_TRANSFER)
                        .action(new StageAction(null, null, null, null, 7L, null, null, null, null))
                        .allowedTransitions(List.of("END")).build(),
                new StageDef("END", "Yakunla", List.of(), List.of())));
        session.setAgentScenarioResolver(agentId -> null);

        new DialogTools(session).transitionTo(null, "SALES");

        assertThat(session.state()).isEqualTo("END");
    }

    @Test
    void aFunctionStageIsLeftForTheModelWithItsTask() {
        DialogSession session = session(flow(
                new StageDef("GREETING", "Salom", List.of("LOOKUP"), List.of()),
                action("LOOKUP", StageType.FUNCTION, "END", new StageAction("lookupDebt", null, null, null, null, null, null, null, null)),
                new StageDef("END", "Yakunla", List.of(), List.of())));

        String result = new DialogTools(session).transitionTo(null, "LOOKUP");

        assertThat(session.state()).isEqualTo("LOOKUP");
        assertThat(result).contains("call the tool lookupDebt now");
        assertThat(StageEntry.describeTask(session.scenario(), session.scenario().findStage("LOOKUP"), 2, Map.of()))
                .contains("Stage LOOKUP is done").contains("END");
    }

    @Test
    void anExtractStageOffersOneParameterPerFieldItReads() {
        ScenarioDefinition scenario = flow(
                new StageDef("GREETING", "Salom", List.of("READ"), List.of()),
                action("READ", StageType.EXTRACT_VARIABLE, "END",
                        new StageAction(null, null, null, null, null, null, List.of("promisedDate"), null, null)),
                new StageDef("END", "Yakunla", List.of(), List.of()));

        ToolDef tool = StageEntry.findExtractionTool(scenario, scenario.findStage("READ"));

        assertThat(tool.name()).isEqualTo(StageEntry.EXTRACT_TOOL_NAME);
        assertThat(tool.params()).singleElement().satisfies(p -> {
            assertThat(p.name()).isEqualTo("promisedDate");
            assertThat(p.type()).isEqualTo("date");
        });
    }

    private DialogSession session(ScenarioDefinition scenario) {
        DialogSession session = DialogSession.builder("chan-1", "uz-UZ")
                .context(new CallContext(Map.of("clientName", "Aziz", "debtAmount", new BigDecimal("1500000")), "goal"))
                .scenario(scenario)
                .callAttemptId(42L)
                .companyId(1L)
                .build();
        session.setDtmfSender(tones::add);
        session.setMidCallSmsSender(texts::add);
        return session;
    }

    private static ScenarioDefinition flow(StageDef... stages) {
        return ScenarioDefinition.builder()
                .stages(List.of(stages))
                .outcomeSchema(List.of(
                        new OutcomeField("res", "string", "Natija"),
                        new OutcomeField("promisedDate", "date", "To'lov sanasi")))
                .rolePrompt("Prompt")
                .build();
    }

    private static StageDef action(String id, StageType type, String next, StageAction action) {
        return StageDef.builder().id(id).type(type).action(action).allowedTransitions(List.of(next)).build();
    }
}
