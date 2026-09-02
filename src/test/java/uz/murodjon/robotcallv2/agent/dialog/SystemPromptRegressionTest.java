package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;

import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.voice.domain.entity.EffectiveVoiceSettings;

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
        DialogSession s = new DialogSession("chan-1", "uz-UZ", null, ScenarioFixtures.fullContext(),
                ScenarioFixtures.debtCollection(), null, null, null, 1L, null, true, "Uysot",
                null, null, EffectiveVoiceSettings.NONE);
        s.setState(stageId);
        return s;
    }

    @Test
    void guardrailBlockCarriesEveryScenarioRuleThenEveryPlatformRule() {
        String prompt = factory.stablePrefix(session("GREETING"));
        int idx = prompt.indexOf("QAT'IY QOIDALAR:");
        assertThat(idx).isPositive();
        String guardrailBlock = prompt.substring(idx);

        List<String> expectedOrder = List.of(
                "Qarz summasini HECH QACHON o'zgartirma",
                "Chegirma, imtiyoz yoki qarz kechirishni HECH QACHON taklif qilma",
                "To'lov muddatini o'zing uzaytirma",
                "recordPaymentPromise'ga ber",
                "Huquqiy oqibatlar",
                "shaxsiy ma'lumotlarini begona odamga aytma",
                "requestHumanTransfer bilan operatorga o'tkaz",
                "asabiylashsa yoki haqorat qilsa",
                "recordWrongPerson chaqiring",
                "recordDoNotCall chaqir"
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
                Map.entry("IDENTITY_CHECK", "Suhbatdosh aynan qarzdor ekanini tasdiqla."),
                Map.entry("DEBT_NOTICE", "Qarz miqdori va muddatini xushmuomala yetkaz."),
                Map.entry("REASON_INQUIRY", "To'lov nega amalga oshmayotgan sababini aniqla."),
                Map.entry("PAYMENT_DATE", "Mijozdan aniq to'lov sanasini ol."),
                Map.entry("CONFIRMATION", "Kelishuvni takrorlab tasdiqla."),
                Map.entry("CLOSING", "Xushmuomala xayrlash."),
                Map.entry("ESCALATE_TO_HUMAN", "Operatorga o'tkazishni bildirib xayrlash."),
                Map.entry("END_CALL", "Qo'ng'iroqni yakunlash.")
        );
        for (StageDef stage : ScenarioFixtures.debtCollection().stages()) {
            String annex = factory.turnAnnex(session(stage.id()));
            assertThat(annex).contains("JORIY BOSQICH: " + stage.id() + " — " + expectedPurpose.get(stage.id()));
            if (stage.allowedTransitions().isEmpty()) {
                assertThat(annex).contains("(yakuniy holat)");
            } else {
                assertThat(annex).contains(String.join(", ", stage.allowedTransitions()));
            }
        }
    }

    @Test
    void factsRenderOneLinePerScenarioFactSchemaEntry() {
        String prompt = factory.stablePrefix(session("GREETING"));

        assertThat(prompt)
                .contains("Ism: Aziz Karimov")
                .contains("Summa: 1500000")
                .contains("Valyuta: so'm")
                .contains("Muddat: 2026-07-01")
                .contains("Shartnoma raqami: UY-2026-00123");
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
