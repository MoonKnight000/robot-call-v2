package uz.murodjon.uysotvoice.agent.dialog;

import uz.murodjon.uysotvoice.scenario.dto.FactField;
import uz.murodjon.uysotvoice.scenario.dto.OutcomeField;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioDefinition;
import uz.murodjon.uysotvoice.scenario.dto.StageDef;
import uz.murodjon.uysotvoice.scenario.dto.ToolDef;
import uz.murodjon.uysotvoice.scenario.dto.ToolParamDef;

import java.util.List;
import java.util.Map;

/** Test-only mirror of the {@code debt-collection} scenario seeded by V4__scenario_binding.sql. */
final class ScenarioFixtures {

    private ScenarioFixtures() {
    }

    static ScenarioDefinition debtCollection() {
        return new ScenarioDefinition(
                List.of(
                        new StageDef("GREETING", "Salomlash, tizim ekaningni ayt, suhbat yozib olinishini bildiring.",
                                List.of("IDENTITY_CHECK", "END_CALL", "ESCALATE_TO_HUMAN"), List.of()),
                        new StageDef("IDENTITY_CHECK", "Suhbatdosh aynan qarzdor ekanini tasdiqla.",
                                List.of("DEBT_NOTICE", "END_CALL", "ESCALATE_TO_HUMAN"), List.of()),
                        new StageDef("DEBT_NOTICE", "Qarz miqdori va muddatini xushmuomala yetkaz.",
                                List.of("REASON_INQUIRY", "ESCALATE_TO_HUMAN", "END_CALL"),
                                List.of("recordPaymentPromise", "recordRefusalReason")),
                        new StageDef("REASON_INQUIRY", "To'lov nega amalga oshmayotgan sababini aniqla.",
                                List.of("PAYMENT_DATE", "ESCALATE_TO_HUMAN", "END_CALL"),
                                List.of("recordPaymentPromise", "recordRefusalReason")),
                        new StageDef("PAYMENT_DATE", "Mijozdan aniq to'lov sanasini ol.",
                                List.of("CONFIRMATION", "ESCALATE_TO_HUMAN", "END_CALL"),
                                List.of("recordPaymentPromise", "recordRefusalReason")),
                        new StageDef("CONFIRMATION", "Kelishuvni takrorlab tasdiqla.",
                                List.of("CLOSING", "PAYMENT_DATE", "ESCALATE_TO_HUMAN"),
                                List.of("recordPaymentPromise", "recordRefusalReason")),
                        new StageDef("CLOSING", "Xushmuomala xayrlash.", List.of("END_CALL"), List.of()),
                        new StageDef("ESCALATE_TO_HUMAN", "Operatorga o'tkazishni bildirib xayrlash.",
                                List.of("END_CALL"), List.of()),
                        new StageDef("END_CALL", "Qo'ng'iroqni yakunlash.", List.of(), List.of())
                ),
                List.of(
                        new FactField("clientName", "string", true),
                        new FactField("debtAmount", "number", true),
                        new FactField("currency", "string", true),
                        new FactField("dueDate", "date", false),
                        new FactField("contractNumber", "string", false)
                ),
                List.of(
                        new ToolDef("recordPaymentPromise", "Mijoz to'lov sanasini va'da qilganda chaqiring", List.of(
                                new ToolParamDef("date", "date", true, "kelajakda bo'lishi shart"),
                                new ToolParamDef("amount", "number", false, null),
                                new ToolParamDef("note", "string", false, null))),
                        new ToolDef("recordRefusalReason", "Mijoz to'lovni rad etganda sababini yozib oling", List.of(
                                new ToolParamDef("reason", "string", true, null)))
                ),
                List.of(
                        new OutcomeField("promisedDate", "date", "Mijoz va'da qilgan to'lov sanasi"),
                        new OutcomeField("promisedAmount", "number", "Mijoz va'da qilgan summa"),
                        new OutcomeField("reasonCode", "string", "Rad etish sababi")
                ),
                "Siz \"Uysot\" kompaniyasining avtomatik qarz undirish ovozli agentisiz.",
                List.of(
                        "Qarz summasini HECH QACHON o'zgartirma. Faqat berilgan raqamni ayt.",
                        "Chegirma, imtiyoz yoki qarz kechirishni HECH QACHON taklif qilma.",
                        "To'lov muddatini o'zing uzaytirma — faqat mijoz aytgan sanani yozib ol.",
                        "Mijoz nisbiy sana aytsa (\"ertaga\", \"dushanba\", \"kelasi oyning 5-sanasi\") — uni "
                                + "BUGUNGI SANAdan hisoblab yyyy-MM-dd ko'rinishida recordPaymentPromise'ga ber. "
                                + "Yilni o'zingdan to'qima.",
                        "Huquqiy oqibatlar, sud, jarima yoki ijro haqida o'zingdan gapirma, qo'rqitma."
                ),
                "Bu qo'ng'iroq avtomatik tizim tomonidan amalga oshirilmoqda va yozib olinmoqda."
        );
    }

    /** Convenience facts map matching the old fixed-{@code CallContext} test fixtures. */
    static CallContext fullContext() {
        return new CallContext(Map.of(
                "clientName", "Aziz Karimov",
                "debtAmount", new java.math.BigDecimal("1500000"),
                "currency", "so'm",
                "dueDate", java.time.LocalDate.of(2026, 7, 1),
                "contractNumber", "UY-2026-00123"
        ), "To'lov sanasini kelishish.");
    }
}
