package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;

import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks in the exact prompt content ROADMAP A.3's generic engine produces for the
 * {@code debt-collection} scenario, matching what {@code V4__scenario_binding.sql}
 * seeds and what {@link ScenarioFixtures#debtCollection()} mirrors. Where the old
 * hardcoded {@code SystemPromptFactory} and the new scenario-driven one produce
 * different text (facts render one-per-line here, generically, instead of the old
 * hand-combined "amount + currency" line; {@code recordWrongPerson} moved from a
 * stage-gated tool to a universal one), that is a deliberate, documented delta — see
 * the migration's comments — not something this test tries to reproduce byte-for-byte
 * against the pre-refactor code.
 */
class SystemPromptRegressionTest {

    private final SystemPromptFactory factory = new SystemPromptFactory();

    private static DialogSession session(String stageId) {
        DialogSession s = DialogSession.builder("chan-1", "uz-UZ")
                .context(ScenarioFixtures.fullContext())
                .scenario(ScenarioFixtures.debtCollection())
                .callAttemptId(1L)
                .companyId(1L)
                .companyName("Uysot")
                .build();
        s.setState(stageId);
        return s;
    }

    @Test
    void guardrailBlockCarriesEveryScenarioRuleThenEveryPlatformRule() {
        String prompt = factory.stablePrefix(session("GREETING"));
        int idx = prompt.indexOf("STRICT RULES:");
        assertThat(idx).isPositive();
        String guardrailBlock = prompt.substring(idx);

        List<String> expectedOrder = List.of(
                "Qarz summasini HECH QACHON o'zgartirma",
                "Chegirma, imtiyoz yoki qarz kechirishni HECH QACHON taklif qilma",
                "To'lov muddatini o'zing uzaytirma",
                "recordPaymentPromise'ga ber",
                "Peniya va shartnoma bekor bo'lish muddatini",
                "Sud, ijro, qora ro'yxat",
                "personal data to anyone else",
                // Not "hand over to an operator with requestHumanTransfer": the penalty
                // guardrail above routes to the same tool, so that fragment no longer
                // identifies the platform rule this line is checking the order of.
                "do not know the answer",
                "angry or insults you",
                "Call recordWrongPerson ONLY",
                "call recordDoNotCall immediately"
        );
        int last = -1;
        for (String fragment : expectedOrder) {
            int at = guardrailBlock.indexOf(fragment);
            assertThat(at).as("guardrail fragment '%s' present", fragment).isPositive();
            assertThat(at).as("guardrail fragment '%s' in order", fragment).isGreaterThan(last);
            last = at;
        }
    }

    @Test
    void everyStagePurposeAndTransitionMatchesTheSeed() {
        Map<String, String> expectedPurpose = Map.ofEntries(
                Map.entry("GREETING", "Salomlash, tizim ekaningni ayt, suhbat yozib olinishini bildiring."),
                Map.entry("IDENTITY_CHECK", "Mijozning shaxsini tasdiqla (masalan: 'Men [Ism] aka bilan gaplashayapmanmi?')."),
                Map.entry("DEBT_NOTICE", "Qarz miqdori va muddatini xushmuomala, lekin QAT'IY yetkaz — "
                        + "summani bir qisqa gapda, muddatni boshqasida ayt, ikkalasini bitta uzun gapga tiqma. "
                        + "Bu tasdiqlatish emas, xabar berish: 'qarzingiz bor ekanmi?', 'to'g'rimi?' deb so'rama. "
                        + "FAKTLARda Peniya berilgan bo'lsa uni ham shu yerda bir gapda ayt (berilmagan bo'lsa "
                        + "peniya haqida umuman gapirma). Shartnoma raqamini mijoz o'zi so'ramasa umuman aytma. "
                        + "Javobing ALBATTA savol bilan tugasin — summani aytib jim qolma; bu bosqichda "
                        + "tasdiqlovchi savol ber ('bu haqda xabaringiz bormidi?', 'eshitib turibsizmi?'), "
                        + "sababni keyingi bosqichda so'raysan."),
                Map.entry("REASON_INQUIRY", "Bu bosqichdagi BIRINCHI savoling aynan sabab haqida bo'lsin: "
                        + "'Nima uchun to'lanmayapti?' yoki 'Sabab nimada?'. SANA so'rash bu bosqichda "
                        + "QAT'IYAN taqiqlanadi — sanani keyingi bosqichda so'raysan. Mijoz sababni "
                        + "aytmaguncha PAYMENT_DATE ga o'tma."),
                Map.entry("PAYMENT_DATE", "Mijozdan aniq to'lov sanasini ol. FAKTLARda 'Shartnoma bekor "
                        + "bo'lishiga qolgan kun' berilgan bo'lsa — sanani so'rashdan oldin uni bir qisqa "
                        + "gapda, tahdidsiz, xotirjam ayt (masalan: 'Yana 30 kun to'lanmasa, shartnoma "
                        + "shartlariga ko'ra bekor qilinadi'). Berilmagan bo'lsa bu haqda umuman gapirma."),
                Map.entry("CONFIRMATION", "Kelishuvni takrorlab tasdiqla."),
                Map.entry("CLOSING", "Bitta qisqa gap bilan xushmuomala xayrlash va shu gapni endCall tool'ining reply parametrida yuborish — xayrlashuv boshqa tool orqali aytilsa qo'ng'iroq uzilmay ochiq qoladi."),
                Map.entry("ESCALATE_TO_HUMAN", "Operatorga o'tkazishni bildirib xayrlash."),
                Map.entry("END_CALL", "Qo'ng'iroqni yakunlash: endCall tool'ini chaqir, boshqa hech narsa aytma.")
        );
        for (StageDef stage : ScenarioFixtures.debtCollection().stages()) {
            String annex = factory.turnAnnex(session(stage.id()));
            assertThat(annex).contains("CURRENT STAGE: " + stage.id() + " — " + expectedPurpose.get(stage.id()));
            if (stage.allowedTransitions().isEmpty()) {
                assertThat(annex).contains("(final state)");
            } else {
                assertThat(annex).contains(String.join(", ", stage.allowedTransitions()));
            }
        }
    }

    @Test
    void factsRenderOneLinePerScenarioFactSchemaEntry() {
        String prompt = factory.stablePrefix(session("GREETING"));

        assertThat(prompt)
                .contains("Name: Aziz Karimov")
                .contains("Amount: 1500000")
                .contains("Currency: so'm")
                .contains("Due date: 2026-07-01")
                .contains("Contract number: UY-2026-00123")
                .contains("Penalty (Peniya): 75000")
                .contains("Days until the contract is cancelled (Shartnoma bekor bo'lishiga qolgan kun): 30");
    }

    @Test
    void toolGatingMatchesTheOldHardcodedPerStateMap() {
        // recordPaymentPromise/recordRefusalReason only in the 4 stages that used to
        // allow them; recordWrongPerson is universal now (documented delta) so it no
        // longer needs to appear in any stage's own allowedTools.
        Map<String, Boolean> expectSpecificTools = Map.of(
                "GREETING", false, "IDENTITY_CHECK", false,
                "DEBT_NOTICE", true, "REASON_INQUIRY", true, "PAYMENT_DATE", true, "CONFIRMATION", true,
                "CLOSING", false, "ESCALATE_TO_HUMAN", false, "END_CALL", false);
        for (StageDef stage : ScenarioFixtures.debtCollection().stages()) {
            boolean hasSpecificTools = stage.allowedTools() != null
                    && stage.allowedTools().contains("recordPaymentPromise");
            assertThat(hasSpecificTools).as("stage %s", stage.id()).isEqualTo(expectSpecificTools.get(stage.id()));
            assertThat(stage.allowedTools()).as("stage %s never lists recordWrongPerson", stage.id())
                    .doesNotContain("recordWrongPerson");
        }
    }
}
