package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uz.murodjon.robotcallv2.aiagent.AiAgentFixtures;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.enums.SpeakingWarmth;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.memory.domain.entity.RememberedCall;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;
import java.time.LocalDate;
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
        return DialogSession.builder("chan-1", language)
                .context(context)
                .scenario(ScenarioFixtures.debtCollection())
                .callAttemptId(1L)
                .companyId(1L)
                .companyName("Uysot")
                .build();
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
        // In the caller's zone, not the server's: on a UTC host an evening call was being
        // told tomorrow's date.
        String prompt = factory.stablePrefix(session(ScenarioFixtures.fullContext(), "uz-UZ"));

        assertThat(prompt).contains(LocalDate.now(SystemPromptFactory.CALLER_ZONE).toString());
    }

    @Test
    void anchorsThePartsOfTheDayToHours() {
        // What a booking call is made of: "ertaga tushdan keyin" has to reach the slot tool
        // as a date and an hour range, and neither is something to leave the model to guess.
        String prompt = factory.stablePrefix(session(ScenarioFixtures.fullContext(), "uz-UZ"));

        assertThat(prompt)
                .contains("CURRENT TIME")
                .contains("(tomorrow): " + LocalDate.now(SystemPromptFactory.CALLER_ZONE).plusDays(1))
                .contains("tushdan keyin / после обеда: 13:00–17:00");
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
                .contains("topic: debt-collection")
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

        assertThat(annex).contains("CURRENT STAGE: " + stageId);
        assertThat(annex).contains("Allowed next stages:");
    }

    private static DialogSession sessionWithWarmth(SpeakingWarmth warmth) {
        AiAgent base = AiAgentFixtures.agent(3L, 1L, 10L, "uz-UZ", "nigora");
        AiAgent agent = base.withAdvanced(base.ambience(), base.callBehaviour(), base.dataPolicy(),
                base.voice().withWarmth(warmth), base.useRag(), base.initiationWebhook(), base.postCallWebhook());
        return DialogSession.builder("chan-1", "uz-UZ")
                .context(ScenarioFixtures.fullContext())
                .scenario(ScenarioFixtures.debtCollection())
                .callAttemptId(1L)
                .companyId(1L)
                .agent(agent)
                .build();
    }

    @Test
    void aReservedAgentKeepsTheOneWordReaction() {
        String prompt = factory.stablePrefix(sessionWithWarmth(SpeakingWarmth.RESERVED));

        assertThat(prompt)
                .contains("Keep the reaction to ONE WORD")
                .doesNotContain("lively spoken register")
                .contains("juda afsusdaman");
    }

    @Test
    void aBalancedAgentReactsToWhatWasSaidButStagesNoSympathy() {
        // The one-word rule read as a machine on the Uzbek voices, which have no emotional
        // roles; the ban on staged sympathy it was written for stays.
        String prompt = factory.stablePrefix(sessionWithWarmth(SpeakingWarmth.BALANCED));

        assertThat(prompt)
                .doesNotContain("Keep the reaction to ONE WORD")
                .contains("Ha, ish bo'lmasa qiyin")
                .contains("lively spoken register")
                .contains("Staged sympathy is forbidden")
                .contains("juda afsusdaman");
    }

    @Test
    void aFriendlyCallerIsAnsweredInKindOnlyByAnAgentThatIsNotReserved() {
        SentimentDetector detector = new SentimentDetector();

        assertThat(detector.empathyDirective(CustomerSentiment.POSITIVE, "uz-UZ", SpeakingWarmth.BALANCED))
                .contains("slightly warmer");
        assertThat(detector.empathyDirective(CustomerSentiment.POSITIVE, "uz-UZ", SpeakingWarmth.RESERVED))
                .isEmpty();
        assertThat(detector.empathyDirective(CustomerSentiment.FRUSTRATED, "uz-UZ", SpeakingWarmth.RESERVED))
                .contains("one short word");
    }

    @Test
    void anOutboundCallIsFramedAsOneTheAgentMade() {
        String prompt = factory.stablePrefix(session(ScenarioFixtures.fullContext(), "uz-UZ"));

        assertThat(prompt)
                .contains("CALL TYPE: OUTBOUND")
                .contains("calling on behalf of \"Uysot\"");
    }

    @Test
    void anInboundCallIsFramedAsOneTheClientMade() {
        // A client who rang in was being opened on like a debtor being dialled: "calling on
        // behalf of", then "am I speaking with Aziz aka?".
        DialogSession s = session(ScenarioFixtures.fullContext(), "uz-UZ");
        s.setInbound(true);

        String prompt = factory.stablePrefix(s);

        assertThat(prompt)
                .contains("CALL TYPE: INBOUND")
                .contains("answering the call on behalf of \"Uysot\"")
                .contains("Sizga qanday yordam bera olaman?")
                .doesNotContain("calling on behalf of")
                .doesNotContain("OUTBOUND");
    }

    @Test
    void theScenariosCallPurposeIsStatedWithItsFactsFilledIn() {
        ScenarioDefinition d = ScenarioFixtures.debtCollection();
        ScenarioDefinition withPurpose = d.toBuilder().callPurpose("{{clientName}} saytda ariza qoldirgan").build();
        DialogSession s = DialogSession.builder("chan-1", "uz-UZ")
                .context(ScenarioFixtures.fullContext())
                .scenario(withPurpose)
                .callAttemptId(1L)
                .companyId(1L)
                .build();

        assertThat(factory.stablePrefix(s)).contains("CALL PURPOSE: Aziz Karimov saytda ariza qoldirgan");
    }

    @Test
    void theAnnexHandsTheNextStagesReadyMadeLineWithItsFactsFilledIn() {
        ScenarioDefinition scenario = new ScenarioDefinition(
                List.of(
                        new StageDef("GREETING", "Salomlashing", List.of("DEBT_NOTICE"), List.of()),
                        StageDef.builder()
                                .id("DEBT_NOTICE")
                                .purpose("Qarzni ayting")
                                .allowedTransitions(List.of("END_CALL"))
                                .allowedTools(List.of())
                                .staticLine("{{clientName}} aka, shartnoma bo'yicha to'lov kechikdi.")
                                .build(),
                        new StageDef("END_CALL", "Tamom", List.of(), List.of())),
                List.of(), List.of(), List.of(), "Agent", List.of(), null);
        DialogSession s = DialogSession.builder("chan-1", "uz-UZ")
                .context(ScenarioFixtures.fullContext())
                .scenario(scenario)
                .callAttemptId(1L)
                .companyId(1L)
                .build();

        assertThat(factory.turnAnnex(s)).contains(
                "If you move to DEBT_NOTICE, say EXACTLY this: «Aziz Karimov aka, shartnoma bo'yicha to'lov kechikdi.»");
    }

    @Test
    void theAnnexListsWhatTheCallHasRecorded() {
        DialogSession s = session(ScenarioFixtures.fullContext(), "uz-UZ");
        s.setState("PAYMENT_DATE");
        assertThat(factory.turnAnnex(s)).doesNotContain("ALREADY ESTABLISHED");

        s.recordOutcome("promisedDate", LocalDate.of(2026, 10, 15));

        assertThat(factory.turnAnnex(s)).contains("ALREADY ESTABLISHED").contains("promisedDate = 2026-10-15");
    }

    @Test
    void anAllowedTransitionCarriesItsCondition() {
        StageDef askTime = StageDef.builder()
                .id("ASK_TIME")
                .purpose("Vaqti bormi so'rang")
                .allowedTransitions(List.of("HAS_TIME", "RESCHEDULE"))
                .allowedTools(List.of())
                .transitionConditions(Map.of("HAS_TIME", "mijoz vaqtim bor desa"))
                .build();

        assertThat(SystemPromptFactory.describeTransitions(askTime))
                .isEqualTo("HAS_TIME (if: mijoz vaqtim bor desa), RESCHEDULE");
    }

    @Test
    void theCachedPrefixDoesNotMoveWithTheState() {
        // The whole point of the split: the provider's context cache keys on this
        // prefix, so a transition must not change a single byte of it.
        DialogSession s = session(ScenarioFixtures.fullContext(), "uz-UZ");
        String atGreeting = factory.stablePrefix(s);

        s.setState("PAYMENT_DATE");

        assertThat(factory.stablePrefix(s)).isEqualTo(atGreeting);
        assertThat(atGreeting).doesNotContain("CURRENT STAGE");
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

        assertThat(factory.turnAnnex(s)).doesNotContain("you asked:");
    }

    @Test
    void aBargeInIsReportedInTheAnnexOnly() {
        DialogSession s = session(ScenarioFixtures.fullContext(), "uz-UZ");
        s.setLastAgentText("Qarzingiz bo'yicha");
        s.setInterrupted(true);

        assertThat(factory.turnAnnex(s)).contains("interrupted").contains("Qarzingiz bo'yicha");
        assertThat(factory.stablePrefix(s)).doesNotContain("Qarzingiz bo'yicha");
    }

    @Test
    void statesTheConversationLanguage() {
        assertThat(factory.stablePrefix(session(ScenarioFixtures.fullContext(), "ru-RU"))).contains("in Russian");
        assertThat(factory.stablePrefix(session(ScenarioFixtures.fullContext(), "uz-UZ"))).contains("in Uzbek");
    }

    @Test
    void theUzbekSpellingRuleOnlyGoesToUzbekCalls() {
        // Real uz-UZ calls produced "so me'doridagi" and "kuninigiz", which TTS then spoke
        // as written. The rule that fixes that says nothing useful on a Russian call.
        assertThat(factory.stablePrefix(session(ScenarioFixtures.fullContext(), "uz-UZ")))
                .contains("apostrophe");
        assertThat(factory.stablePrefix(session(ScenarioFixtures.fullContext(), "ru-RU")))
                .doesNotContain("apostrophe");
    }

    @Test
    void speaksTheConversationalStyleRuleInTheCallsLanguage() {
        // The rule itself is always there; its example words must follow the call's
        // language, or the model drops Uzbek back-channels into a Russian call.
        assertThat(factory.stablePrefix(session(ScenarioFixtures.fullContext(), "uz-UZ")))
                .contains("open your reply with a brief reaction")
                .contains("Tushunarli")
                .doesNotContain("Понятно");
        assertThat(factory.stablePrefix(session(ScenarioFixtures.fullContext(), "ru-RU")))
                .contains("open your reply with a brief reaction")
                .contains("Понятно")
                .doesNotContain("Tushunarli");
    }

    @Test
    void requiresEveryReplyToEndOnAQuestion() {
        // A real DEBT_NOTICE turn stated the sum and the missed due date and then stopped.
        // The caller had nothing to answer, said one unintelligible word, and the call
        // never recovered — so "always end on a question" is a rule, not a stage's hope.
        assertThat(factory.stablePrefix(session(ScenarioFixtures.fullContext(), "uz-UZ")))
                .contains("MUST end with a question")
                .contains("bu haqda xabaringiz bormidi?");
        assertThat(factory.stablePrefix(session(ScenarioFixtures.fullContext(), "ru-RU")))
                .contains("MUST end with a question")
                .contains("вы в курсе?");
    }

    @Test
    void instructsNaturalIdentityCheckAndForbidsRoboticPhrasing() {
        String prompt = factory.stablePrefix(session(ScenarioFixtures.fullContext(), "uz-UZ"));

        assertThat(prompt)
                .contains("Siz [Ism]misiz?")
                .contains("Assalomu alaykum, Murodjon aka, yaxshimisiz?")
                .contains("suhbatdoshim");
    }

    @Test
    void showsHowToHandleARepairRequestAWhoIsThisAndAnUnfinishedThought() {
        // "adashibmiz" on a "kim bu?" and a full re-greeting on "nima dedingiz?" came from
        // real calls; the pairs put the right reply next to each.
        String prompt = factory.stablePrefix(session(ScenarioFixtures.fullContext(), "uz-UZ"));

        assertThat(prompt)
                .contains("Nima dedingiz?")
                .contains("\"Uzr, adashibmiz.\"")
                .contains("call waitForCaller and let them finish");
    }

    @Test
    void tellsTheModelNotToRestateFactsItHasAlreadySpoken() {
        // Asked "allo", the bot re-delivered the whole debt notice. The stage purpose is
        // re-sent every turn and reads as an order to state it again, so the counter-rule
        // has to be present — and it must not forbid the closing confirmation.
        String prompt = factory.stablePrefix(session(ScenarioFixtures.fullContext(), "uz-UZ"));

        assertThat(prompt).contains("Do not restate").contains("Exception");
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
        assertThat(factory.stablePrefix(s)).doesNotContain("already been spoken");

        s.setDisclosureSpoken(true);
        assertThat(factory.stablePrefix(s))
                .contains("already been spoken")
                // Scoped to the first reply, the rule was obeyed to the letter and broken
                // in spirit: the model introduced itself on the second reply instead. It
                // has to hold for the whole call.
                .contains("NOWHERE IN THE CALL")
                // A bare "do not repeat it" did not stop the model opening with a
                // greeting anyway; what the first line must be instead has to be there.
                .contains("Your first reply");
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
        DialogSession s = DialogSession.builder("chan-2", "uz-UZ")
                .context(new CallContext(Map.of("topicName", "xizmat sifati"), null))
                .scenario(survey)
                .callAttemptId(1L)
                .companyId(1L)
                .companyName("Uysot")
                .build();

        String prompt = factory.stablePrefix(s);

        assertThat(prompt).contains("so'rovnoma o'tkazadigan agentisiz");
        assertThat(prompt).contains("xizmat sifati");
        assertThat(prompt).doesNotContain("qarz undirish");
        // UNIVERSAL-OPERATOR-PLAN A.2: nor the debt call's wording, which used to be platform text.
        assertThat(prompt).doesNotContain("to'lanmayapti").doesNotContain("qarzingiz bor")
                .doesNotContain("recordPaymentPromise");
    }

    @Test
    void theScenariosOwnPhrasingExamplesFollowTheCallsLanguage() {
        String uz = factory.stablePrefix(session(ScenarioFixtures.fullContext(), "uz-UZ"));
        String ru = factory.stablePrefix(session(ScenarioFixtures.fullContext(), "ru-RU"));

        assertThat(uz).contains("\"Qachon to'lay olasiz?\"").doesNotContain("Когда сможете оплатить?");
        assertThat(ru).contains("\"Когда сможете оплатить?\"").doesNotContain("\"Qachon to'lay olasiz?\"");
    }

    @Test
    void humanLikePersonaConfiguresNameAndProhibitsRobotIdentity() {
        DialogSession session = DialogSession.builder("chan-1", "uz")
                .ttsVoice("dilnavoz")
                .context(ScenarioFixtures.fullContext())
                .scenario(ScenarioFixtures.debtCollection())
                .callAttemptId(1L)
                .companyId(1L)
                .disclosureEnabled(false)
                .companyName("Uysot")
                .agentPersona(AgentPersona.HUMAN_LIKE)
                .build();

        String prompt = factory.stablePrefix(session);

        assertThat(prompt).contains("PERSONA AND ROLE (A REAL HUMAN SPECIALIST)");
        assertThat(prompt).contains("Dilnavoz");
        assertThat(prompt).contains("STRICTLY FORBIDDEN: calling yourself a robot, bot, artificial intelligence or automated system");
        assertThat(prompt).contains("Salom, mening ismim Dilnavoz. Uysot kompaniyasidan qo'ng'iroq qilyapman");
    }

    @Test
    void aProviderVoiceIdIsNotUsedAsAPersonName() {
        // Gemini Live's voices are Fenrir, Charon, Kore, Zephyr. Title-cased, one of them
        // introduced itself on a real call: "Men Default kompaniyasidan Fenrirman".
        assertThat(SystemPromptFactory.resolveVoicePersonaName("Fenrir", "uz-UZ")).isEqualTo("Dilnoza");
        assertThat(SystemPromptFactory.resolveVoicePersonaName("Puck", "ru-RU")).isEqualTo("Анна");
        assertThat(SystemPromptFactory.resolveVoicePersonaName("nigora", "uz-UZ")).isEqualTo("Nigora");
    }

    @Test
    void aiAssistantPersonaConfiguresRobotDisclosureAndFallback() {
        DialogSession session = DialogSession.builder("chan-1", "uz")
                .ttsVoice("dilnavoz")
                .context(ScenarioFixtures.fullContext())
                .scenario(ScenarioFixtures.debtCollection())
                .callAttemptId(1L)
                .companyId(1L)
                .disclosureEnabled(true)
                .companyName("Uysot")
                .agentPersona(AgentPersona.AI_ASSISTANT)
                .build();

        String prompt = factory.stablePrefix(session);
        assertThat(prompt).contains("PERSONA AND ROLE (ARTIFICIAL INTELLIGENCE / AI ASSISTANT)");
        assertThat(prompt).contains("Men sun'iy intellekt yordamchisiman, ushbu savolingiz bo'yicha aniq ma'lumotga ega emasman");
    }

    @Test
    void aScenarioHandoffStartsTheTargetsFirstStageAndRebuildsThePrefix() {
        DialogSession s = session(ScenarioFixtures.fullContext(), "uz-UZ");
        s.setSystemPrefix(factory.stablePrefix(s));
        ScenarioDefinition survey = new ScenarioDefinition(
                List.of(new StageDef("Q1", "Birinchi savolni bering", List.of(), List.of())),
                List.of(), List.of(), List.of(), "Survey agent", List.of(), null);

        s.switchScenario(survey);

        assertThat(s.scenario()).isSameAs(survey);
        assertThat(s.state()).isEqualTo("Q1");
        assertThat(s.systemPrefix()).isNull();
        assertThat(factory.turnAnnex(s)).contains("Q1").contains("Birinchi savolni bering");
    }

    @Test
    void anOperatorWhisperRidesEveryTurnUntilReplaced() {
        DialogSession s = session(ScenarioFixtures.fullContext(), "uz-UZ");
        assertThat(factory.turnAnnex(s)).doesNotContain("OPERATOR INSTRUCTION");

        s.setOperatorWhisper("10% chegirma taklif qiling");
        assertThat(factory.turnAnnex(s)).contains("OPERATOR INSTRUCTION").contains("10% chegirma taklif qiling");

        s.setOperatorWhisper("Muddatni 5-sanaga suring");
        assertThat(factory.turnAnnex(s)).contains("Muddatni 5-sanaga suring").doesNotContain("10% chegirma");
    }
}
