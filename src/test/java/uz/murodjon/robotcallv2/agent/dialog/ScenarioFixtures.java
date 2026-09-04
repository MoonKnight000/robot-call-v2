package uz.murodjon.robotcallv2.agent.dialog;

import uz.murodjon.robotcallv2.scenario.domain.entity.FactField;
import uz.murodjon.robotcallv2.scenario.domain.entity.OutcomeField;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.scenario.domain.entity.ToolDef;
import uz.murodjon.robotcallv2.scenario.domain.entity.ToolParamDef;

import java.util.List;
import java.util.Map;

/**
 * Test-only mirror of the {@code debt-collection} scenario seeded by
 * {@code R__seed_data.sql}. Kept byte-identical to the seed's stage purposes and
 * guardrails on purpose: {@link SystemPromptRegressionTest} asserts the prompt the
 * engine builds from it, so a fixture that drifts from the seed locks in text no real
 * call ever sees. When the seed changes, this changes in the same commit.
 */
final class ScenarioFixtures {

    private ScenarioFixtures() {
    }

    static ScenarioDefinition debtCollection() {
        return new ScenarioDefinition(
                List.of(
                        new StageDef("GREETING", "Salomlash, tizim ekaningni ayt, suhbat yozib olinishini bildiring.",
                                List.of("IDENTITY_CHECK", "END_CALL", "ESCALATE_TO_HUMAN"), List.of()),
                        new StageDef("IDENTITY_CHECK", "Mijozning shaxsini tasdiqla (masalan: 'Men [Ism] aka bilan gaplashayapmanmi?').",
                                List.of("DEBT_NOTICE", "END_CALL", "ESCALATE_TO_HUMAN"), List.of()),
                        new StageDef("DEBT_NOTICE", "Qarz miqdori va muddatini xushmuomala, lekin QAT'IY yetkaz — "
                                + "summani bir qisqa gapda, muddatni boshqasida ayt, ikkalasini bitta uzun gapga tiqma. "
                                + "Bu tasdiqlatish emas, xabar berish: 'qarzingiz bor ekanmi?', 'to'g'rimi?' deb so'rama. "
                                + "FAKTLARda Peniya berilgan bo'lsa uni ham shu yerda bir gapda ayt (berilmagan bo'lsa "
                                + "peniya haqida umuman gapirma). Shartnoma raqamini mijoz o'zi so'ramasa umuman aytma. "
                                + "Javobing ALBATTA savol bilan tugasin — summani aytib jim qolma; bu bosqichda "
                                + "tasdiqlovchi savol ber ('bu haqda xabaringiz bormidi?', 'eshitib turibsizmi?'), "
                                + "sababni keyingi bosqichda so'raysan.",
                                List.of("REASON_INQUIRY", "ESCALATE_TO_HUMAN", "END_CALL"),
                                List.of("recordPaymentPromise", "recordRefusalReason")),
                        new StageDef("REASON_INQUIRY", "Bu bosqichdagi BIRINCHI savoling aynan sabab haqida bo'lsin: "
                                + "'Nima uchun to'lanmayapti?' yoki 'Sabab nimada?'. SANA so'rash bu bosqichda "
                                + "QAT'IYAN taqiqlanadi — sanani keyingi bosqichda so'raysan. Mijoz sababni "
                                + "aytmaguncha PAYMENT_DATE ga o'tma.",
                                List.of("PAYMENT_DATE", "ESCALATE_TO_HUMAN", "END_CALL"),
                                List.of("recordPaymentPromise", "recordRefusalReason")),
                        new StageDef("PAYMENT_DATE", "Mijozdan aniq to'lov sanasini ol. FAKTLARda 'Shartnoma bekor "
                                + "bo'lishiga qolgan kun' berilgan bo'lsa — sanani so'rashdan oldin uni bir qisqa "
                                + "gapda, tahdidsiz, xotirjam ayt (masalan: 'Yana 30 kun to'lanmasa, shartnoma "
                                + "shartlariga ko'ra bekor qilinadi'). Berilmagan bo'lsa bu haqda umuman gapirma.",
                                List.of("CONFIRMATION", "ESCALATE_TO_HUMAN", "END_CALL"),
                                List.of("recordPaymentPromise", "recordRefusalReason")),
                        new StageDef("CONFIRMATION", "Kelishuvni takrorlab tasdiqla.",
                                List.of("CLOSING", "PAYMENT_DATE", "ESCALATE_TO_HUMAN"),
                                List.of("recordPaymentPromise", "recordRefusalReason")),
                        new StageDef("CLOSING", "Bitta qisqa gap bilan xushmuomala xayrlash va shu gapni endCall tool'ining reply parametrida yuborish — xayrlashuv boshqa tool orqali aytilsa qo'ng'iroq uzilmay ochiq qoladi.",
                                List.of("END_CALL"), List.of()),
                        new StageDef("ESCALATE_TO_HUMAN", "Operatorga o'tkazishni bildirib xayrlash.",
                                List.of("END_CALL"), List.of()),
                        new StageDef("END_CALL", "Qo'ng'iroqni yakunlash: endCall tool'ini chaqir, boshqa hech narsa aytma.",
                                List.of(), List.of())
                ),
                List.of(
                        new FactField("clientName", "string", true),
                        new FactField("debtAmount", "number", true),
                        new FactField("currency", "string", true),
                        new FactField("dueDate", "date", false),
                        new FactField("contractNumber", "string", false),
                        new FactField("penaltyAmount", "number", false),
                        new FactField("contractCancelDays", "number", false)
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
                        "Peniya va shartnoma bekor bo'lish muddatini FAQAT FAKTLARda berilgan bo'lsa va aynan "
                                + "berilgan raqam bilan ayt — o'zingdan raqam to'qima, foiz hisoblama. Ohang "
                                + "xotirjam xabar berish bo'lsin, tahdid emas.",
                        "Sud, ijro, qora ro'yxat, mol-mulk musodarasi va jinoiy javobgarlik haqida o'zingdan "
                                + "gapirma va mijozni qo'rqitma. Mijoz shu haqda so'rasa — requestHumanTransfer "
                                + "bilan operatorga o'tkaz.",
                        "Mijozga 'suhbatdoshim' yoki 'mijoz' deb gapirma. 'Siz [Ism]misiz?' deb so'rama — xuddi "
                                + "tirik operatordek: 'Men [Ism] aka bilan gaplashayapmanmi?' yoki '[Ism] aka, "
                                + "sizmisiz?' deb so'ra."
                ),
                null
        );
    }

    /** Every fact the seed's schema declares, as a target that carries all of them would. */
    static CallContext fullContext() {
        return new CallContext(Map.of(
                "clientName", "Aziz Karimov",
                "debtAmount", new java.math.BigDecimal("1500000"),
                "currency", "so'm",
                "dueDate", java.time.LocalDate.of(2026, 7, 1),
                "contractNumber", "UY-2026-00123",
                "penaltyAmount", new java.math.BigDecimal("75000"),
                "contractCancelDays", new java.math.BigDecimal("30")
        ), "To'lov sanasini kelishish.");
    }
}
