package uz.murodjon.uysotvoice.agent.dialog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import uz.murodjon.uysotvoice.scenario.dto.ScenarioDefinition;
import uz.murodjon.uysotvoice.scenario.dto.StageDef;
import uz.murodjon.uysotvoice.voice.dto.EffectiveVoiceSettings;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The prompt carries the call's facts and the guardrails. If a fact goes missing
 * the model invents one, and §4.4 says the numbers are never the model's to choose.
 * The prefix/annex split additionally has to hold: the prefix is what the provider
 * caches, so it must not move when the FSM does.
 */
class SystemPromptFactoryTest {

    private final SystemPromptFactory factory = new SystemPromptFactory();

    private static DialogSession session(CallContext context, String language) {
        return new DialogSession("chan-1", language, null, context, ScenarioFixtures.debtCollection(),
                null, null, null, 1L, null, true, null, EffectiveVoiceSettings.NONE);
    }

    @Test
    void carriesEveryFactVerbatim() {
        String prompt = factory.stablePrefix(session(ScenarioFixtures.fullContext(), "uz-UZ"));

        assertThat(prompt)
                .contains("Aziz Karimov")
                .contains("1500000")
                .contains("so'm")
                .contains("2026-07-01")
                .contains("UY-2026-00123")
                .contains("To'lov sanasini kelishish.");
    }

    @Test
    void namesTodaysDate() {
        // Without it the model invents a year for "kelasi oyning 5-sanasi" and the
        // recordPaymentPromise guardrail then rejects its own answer as a past date.
        String prompt = factory.stablePrefix(session(ScenarioFixtures.fullContext(), "uz-UZ"));

        assertThat(prompt).contains(java.time.LocalDate.now().toString());
    }

    @Test
    void missingFactsBecomeDashesRatherThanNull() {
        CallContext empty = new CallContext(Map.of(), null);

        String prompt = factory.stablePrefix(session(empty, "uz-UZ"));

        assertThat(prompt).doesNotContain("null");
        assertThat(prompt).contains("—");
    }

    private static Stream<String> debtCollectionStageIds() {
        return ScenarioFixtures.debtCollection().stages().stream().map(StageDef::id);
    }

    @ParameterizedTest
    @MethodSource("debtCollectionStageIds")
    void everyStageHasAPurposeAndAllowedTransitions(String stageId) {
        DialogSession s = session(ScenarioFixtures.fullContext(), "uz-UZ");
        s.setState(stageId);

        String annex = factory.turnAnnex(s);

        assertThat(annex).contains("JORIY BOSQICH: " + stageId);
        assertThat(annex).contains("Ruxsat etilgan keyingi bosqichlar:");
    }

    @Test
    void theCachedPrefixDoesNotMoveWithTheState() {
        // The whole point of the split: the provider's context cache keys on this
        // prefix, so a transition must not change a single byte of it.
        DialogSession s = session(ScenarioFixtures.fullContext(), "uz-UZ");
        String atGreeting = factory.stablePrefix(s);

        s.setState("PAYMENT_DATE");

        assertThat(factory.stablePrefix(s)).isEqualTo(atGreeting);
        assertThat(atGreeting).doesNotContain("JORIY BOSQICH");
    }

    @Test
    void aBargeInIsReportedInTheAnnexOnly() {
        DialogSession s = session(ScenarioFixtures.fullContext(), "uz-UZ");
        s.setLastAgentText("Qarzingiz bo'yicha");
        s.setInterrupted(true);

        assertThat(factory.turnAnnex(s)).contains("bo'ldi").contains("Qarzingiz bo'yicha");
        assertThat(factory.stablePrefix(s)).doesNotContain("Qarzingiz bo'yicha");
    }

    @Test
    void statesTheConversationLanguage() {
        assertThat(factory.stablePrefix(session(ScenarioFixtures.fullContext(), "ru-RU"))).contains("rus tilida");
        assertThat(factory.stablePrefix(session(ScenarioFixtures.fullContext(), "uz-UZ"))).contains("o'zbek tilida");
    }

    @Test
    void alwaysCarriesTheGuardrails() {
        String prompt = factory.stablePrefix(session(ScenarioFixtures.fullContext(), "uz-UZ"));

        assertThat(prompt)
                .contains("Chegirma")            // never offer a discount (scenario guardrail)
                .contains("recordDoNotCall")     // opt-out route (§11.4, platform guardrail)
                .contains("requestHumanTransfer"); // human escalation must always exist (platform guardrail)
    }

    @Test
    void tellsTheModelNotToRepeatASpokenDisclosure() {
        // §11.1: the disclosure is spoken from code; repeating it sounds broken.
        DialogSession s = session(ScenarioFixtures.fullContext(), "uz-UZ");
        assertThat(factory.stablePrefix(s)).doesNotContain("TAKRORLAMA");

        s.setDisclosureSpoken(true);
        assertThat(factory.stablePrefix(s)).contains("TAKRORLAMA");
    }

    @Test
    void aDifferentScenarioGetsItsOwnRoleAndFacts() {
        // ROADMAP A.3: the prompt is entirely scenario-driven, not just debt-collection.
        ScenarioDefinition survey = new ScenarioDefinition(
                java.util.List.of(new StageDef("GREETING", "Salomlashish", java.util.List.of(), null)),
                java.util.List.of(new uz.murodjon.uysotvoice.scenario.dto.FactField("topicName", "string", false)),
                java.util.List.of(),
                java.util.List.of(new uz.murodjon.uysotvoice.scenario.dto.OutcomeField("score", "number", "baho")),
                "Siz so'rovnoma o'tkazadigan agentisiz.",
                java.util.List.of("Savollarni neytral tarzda bering"),
                "disclosure");
        DialogSession s = new DialogSession("chan-2", "uz-UZ", null,
                new CallContext(Map.of("topicName", "xizmat sifati"), null), survey, null, null, null, 1L, null, true,
                null, EffectiveVoiceSettings.NONE);

        String prompt = factory.stablePrefix(s);

        assertThat(prompt).contains("so'rovnoma o'tkazadigan agentisiz");
        assertThat(prompt).contains("xizmat sifati");
        assertThat(prompt).doesNotContain("qarz undirish");
    }
}
