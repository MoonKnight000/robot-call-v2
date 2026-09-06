package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.memory.domain.entity.RememberedCall;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.voice.domain.entity.EffectiveVoiceSettings;

import java.time.Instant;
import java.util.List;
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
                null, null, null, 1L, 1L, null, true, "Uysot", null, null, EffectiveVoiceSettings.NONE);
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

    @Test
    void injectsMultiCallMemoryWhenPresent() {
        ClientMemory memory = new ClientMemory(1L, 1L, "+998901234567", "Aziz aka", null,
                "Mijoz bilan xushmuomala gaplashing, aka deb murojaat qiling",
                List.of(new RememberedCall(Instant.parse("2026-08-20T09:00:00Z"), "debt-collection",
                        Disposition.PROMISE_TO_PAY, "O'tgan safar dushanba kuni to'lashga va'da bergandi")),
                Map.of("promisedDate", "2026-08-25"), null);
        CallContext contextWithMemory = new CallContext(Map.of("clientName", "Aziz Karimov"),
                "To'lov sanasini kelishish.", memory);

        String prompt = factory.stablePrefix(session(contextWithMemory, "uz-UZ"));

        assertThat(prompt)
                .contains("MULTI-CALL MEMORY")
                .contains("Aziz aka")
                .contains("xushmuomala gaplashing")
                .contains("2026-08-20")
                .contains("PROMISE_TO_PAY")
                .contains("dushanba kuni to'lashga va'da bergandi")
                .contains("2026-08-25");
    }

    @Test
    void firstConversationCarriesNoMemoryBlock() {
        String prompt = factory.stablePrefix(session(ScenarioFixtures.fullContext(), "uz-UZ"));

        assertThat(prompt).doesNotContain("MULTI-CALL MEMORY");
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
    void theAnnexRepeatsTheQuestionTheAgentJustAsked() {
        // Call 9 asked "qachon to'lay olasiz?" in REASON_INQUIRY and asked it again, in
        // other words, in PAYMENT_DATE. The history held both; the model still repeated
        // itself, so the last question is put next to the generation point.
        DialogSession s = session(ScenarioFixtures.fullContext(), "uz-UZ");
        s.setLastAgentText("Tushunarli. Bu to'lovni qachon to'lab bera olasiz?");

        assertThat(factory.turnAnnex(s))
                .contains("Bu to'lovni qachon to'lab bera olasiz?")
                .doesNotContain("Tushunarli.");
    }

    @Test
    void theAnnexSaysNothingWhenTheLastReplyAskedNothing() {
        DialogSession s = session(ScenarioFixtures.fullContext(), "uz-UZ");
        s.setLastAgentText("Yaxshi, belgilab qo'ydim.");

        assertThat(factory.turnAnnex(s)).doesNotContain("so'ragan edingiz");
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
    void theUzbekSpellingRuleOnlyGoesToUzbekCalls() {
        // Real uz-UZ calls produced "so me'doridagi" and "kuninigiz", which TTS then spoke
        // as written. The rule that fixes that says nothing useful on a Russian call.
        assertThat(factory.stablePrefix(session(ScenarioFixtures.fullContext(), "uz-UZ")))
                .contains("apostrof");
        assertThat(factory.stablePrefix(session(ScenarioFixtures.fullContext(), "ru-RU")))
                .doesNotContain("apostrof");
    }

    @Test
    void speaksTheConversationalStyleRuleInTheCallsLanguage() {
        // The rule itself is always there; its example words must follow the call's
        // language, or the model drops Uzbek back-channels into a Russian call.
        assertThat(factory.stablePrefix(session(ScenarioFixtures.fullContext(), "uz-UZ")))
                .contains("munosabat bilan boshlang")
                .contains("Tushunarli")
                .doesNotContain("Понятно");
        assertThat(factory.stablePrefix(session(ScenarioFixtures.fullContext(), "ru-RU")))
                .contains("munosabat bilan boshlang")
                .contains("Понятно")
                .doesNotContain("Tushunarli");
    }

    @Test
    void requiresEveryReplyToEndOnAQuestion() {
        // A real DEBT_NOTICE turn stated the sum and the missed due date and then stopped.
        // The caller had nothing to answer, said one unintelligible word, and the call
        // never recovered — so "always end on a question" is a rule, not a stage's hope.
        assertThat(factory.stablePrefix(session(ScenarioFixtures.fullContext(), "uz-UZ")))
                .contains("ALBATTA savol bilan tugasin")
                .contains("bu haqda xabaringiz bormidi?");
        assertThat(factory.stablePrefix(session(ScenarioFixtures.fullContext(), "ru-RU")))
                .contains("ALBATTA savol bilan tugasin")
                .contains("вы в курсе?");
    }

    @Test
    void instructsNaturalIdentityCheckAndForbidsRoboticPhrasing() {
        String prompt = factory.stablePrefix(session(ScenarioFixtures.fullContext(), "uz-UZ"));

        assertThat(prompt)
                .contains("Siz [Ism]misiz?")
                .contains("Men Murodjon aka bilan gaplashayapmanmi?")
                .contains("suhbatdoshim");
    }

    @Test
    void tellsTheModelNotToRestateFactsItHasAlreadySpoken() {
        // Asked "allo", the bot re-delivered the whole debt notice. The stage purpose is
        // re-sent every turn and reads as an order to state it again, so the counter-rule
        // has to be present — and it must not forbid the closing confirmation.
        String prompt = factory.stablePrefix(session(ScenarioFixtures.fullContext(), "uz-UZ"));

        assertThat(prompt).contains("qayta aytmang").contains("Istisno");
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
        // §11.1: the disclosure is spoken from code; repeating it sounds broken. Keyed on
        // the block's own wording rather than on the anti-repetition rule further down,
        // which every call carries anyway.
        DialogSession s = session(ScenarioFixtures.fullContext(), "uz-UZ");
        assertThat(factory.stablePrefix(s)).doesNotContain("allaqachon aytildi");

        s.setDisclosureSpoken(true);
        assertThat(factory.stablePrefix(s))
                .contains("allaqachon aytildi")
                // A bare "do not repeat it" did not stop the model opening with a
                // greeting anyway; what the first line must be instead has to be there.
                .contains("BOSHLAMA");
    }

    @Test
    void aDifferentScenarioGetsItsOwnRoleAndFacts() {
        // ROADMAP A.3: the prompt is entirely scenario-driven, not just debt-collection.
        ScenarioDefinition survey = new ScenarioDefinition(
                java.util.List.of(new StageDef("GREETING", "Salomlashish", java.util.List.of(), null)),
                java.util.List.of(new uz.murodjon.robotcallv2.scenario.domain.entity.FactField("topicName", "string", false)),
                java.util.List.of(),
                java.util.List.of(new uz.murodjon.robotcallv2.scenario.domain.entity.OutcomeField("score", "number", "baho")),
                "Siz so'rovnoma o'tkazadigan agentisiz.",
                java.util.List.of("Savollarni neytral tarzda bering"),
                "disclosure");
        DialogSession s = new DialogSession("chan-2", "uz-UZ", null,
                new CallContext(Map.of("topicName", "xizmat sifati"), null), survey, null, null, null, 1L, 1L, null, true,
                "Uysot", null, null, EffectiveVoiceSettings.NONE);

        String prompt = factory.stablePrefix(s);

        assertThat(prompt).contains("so'rovnoma o'tkazadigan agentisiz");
        assertThat(prompt).contains("xizmat sifati");
        assertThat(prompt).doesNotContain("qarz undirish");
    }

    @Test
    void humanLikePersonaConfiguresNameAndProhibitsRobotIdentity() {
        DialogSession session = new DialogSession(
                "chan-1", "uz", "dilnavoz", ScenarioFixtures.fullContext(), ScenarioFixtures.debtCollection(),
                null, null, null, 1L, 1L, null, false, "Uysot", null, null,
                EffectiveVoiceSettings.NONE, true, AgentPersona.HUMAN_LIKE, Map.of()
        );

        String prompt = factory.stablePrefix(session);

        assertThat(prompt).contains("SHAXSIYAT VA ROL (HAQIQIY INSON MUTAXASSIS)");
        assertThat(prompt).contains("Dilnavoz");
        assertThat(prompt).contains("QAT'IYAN TAQIQLANADI: O'zingizni robot, bot, sun'iy intellekt yoki avtomatlashtirilgan tizim deb atash");
        assertThat(prompt).contains("Salom, mening ismim Dilnavoz. Uysot kompaniyasidan qo'ng'iroq qilyapman");
    }

    @Test
    void aiAssistantPersonaConfiguresRobotDisclosureAndFallback() {
        DialogSession session = new DialogSession(
                "chan-1", "uz", "dilnavoz", ScenarioFixtures.fullContext(), ScenarioFixtures.debtCollection(),
                null, null, null, 1L, 1L, null, true, "Uysot", null, null,
                EffectiveVoiceSettings.NONE, true, AgentPersona.AI_ASSISTANT, Map.of()
        );

        String prompt = factory.stablePrefix(session);
        assertThat(prompt).contains("SHAXSIYAT VA ROL (SUN'IY INTELLEKT / AI ASSISTENT)");
        assertThat(prompt).contains("Men sun'iy intellekt yordamchisiman, ushbu savolingiz bo'yicha aniq ma'lumotga ega emasman");
    }
}
