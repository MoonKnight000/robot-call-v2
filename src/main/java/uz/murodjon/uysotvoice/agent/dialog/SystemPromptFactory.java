package uz.murodjon.uysotvoice.agent.dialog;

import org.springframework.stereotype.Component;
import uz.murodjon.uysotvoice.shared.dialog.DialogState;

/**
 * Builds the per-turn system prompt from a session's facts and FSM state
 * (PROJECT.md §4.1, §4.4). Rebuilt every turn because the current state, its
 * objective, and the allowed next states change as the conversation advances.
 */
@Component
public class SystemPromptFactory {

    public String build(DialogSession s) {
        CallContext c = s.context();
        StringBuilder sb = new StringBuilder();

        sb.append("Siz \"Uysot\" kompaniyasining avtomatik qarz undirish ovozli agentisiz. ")
                .append("Telefon orqali mijoz bilan ").append(languageName(s.language()))
                .append(" tilida tabiiy suhbatlashasiz.\n\n");

        sb.append("MIJOZ MA'LUMOTLARI (FAKTLAR — faqat shu raqamlarni ayting, o'zgartirmang):\n");
        sb.append("- Ism: ").append(orDash(c.clientName())).append('\n');
        sb.append("- Qarz summasi: ").append(orDash(c.debtAmount()))
                .append(c.currency() != null ? " " + c.currency() : "").append('\n');
        sb.append("- Dastlabki to'lov muddati: ").append(orDash(c.dueDate())).append('\n');
        sb.append("- Shartnoma raqami: ").append(orDash(c.contractNumber())).append('\n');
        if (c.goal() != null && !c.goal().isBlank()) {
            sb.append("- Kampaniya maqsadi: ").append(c.goal()).append('\n');
        }

        sb.append("\nJORIY BOSQICH: ").append(s.state()).append(" — ").append(objective(s.state())).append('\n');
        sb.append("Ruxsat etilgan keyingi bosqichlar: ").append(allowedNext(s.state())).append('\n');
        sb.append("Bosqichni o'zgartirish kerak bo'lsa transitionTo tool'ini chaqiring.\n\n");

        sb.append("QAT'IY QOIDALAR:\n");
        sb.append("- Qarz summasini HECH QACHON o'zgartirma. Faqat berilgan raqamni ayt.\n");
        sb.append("- Chegirma, imtiyoz yoki qarz kechirishni HECH QACHON taklif qilma.\n");
        sb.append("- To'lov muddatini o'zing uzaytirma — faqat mijoz aytgan sanani yozib ol.\n");
        sb.append("- Huquqiy oqibatlar, sud, jarima yoki ijro haqida o'zingdan gapirma, qo'rqitma.\n");
        sb.append("- Mijozning shaxsiy ma'lumotlarini begona odamga (qarzdor bo'lmagan kishiga) aytma.\n");
        sb.append("- Savolga javobni bilmasang — requestHumanTransfer bilan operatorga o'tkaz, o'ylab topma.\n");
        sb.append("- Mijoz asabiylashsa yoki haqorat qilsa — darhol requestHumanTransfer chaqir.\n");
        sb.append("- Telefondagi odam qarzdor emas bo'lsa — recordWrongPerson chaqir.\n\n");

        sb.append("USLUB: qisqa, hurmatli, tabiiy jumlalar. Bir vaqtda bitta savol ber. ")
                .append("Ovozga aylantiriladi — qisqa gaplar tuz, ro'yxat yoki maxsus belgilar ishlatma.\n");
        sb.append("HAR BIR javobing mijozga ovoz bilan aytiladigan matn bo'lishi SHART — matnsiz javob ")
                .append("qaytarma. Tool chaqirish (bosqich o'tkazish, va'da/sabab yozish, yakunlash) matnning ")
                .append("o'rnini bosmaydi: kerakli tool'ni chaqir VA aytadigan gapingni ham yoz.");

        return sb.toString();
    }

    private static String objective(DialogState state) {
        return switch (state) {
            case GREETING -> "Salomlash, tizim ekaningni ayt, suhbat yozib olinishini bildiring.";
            case IDENTITY_CHECK -> "Suhbatdosh aynan qarzdor ekanini tasdiqla.";
            case DEBT_NOTICE -> "Qarz miqdori va muddatini xushmuomala yetkaz.";
            case REASON_INQUIRY -> "To'lov nega amalga oshmayotgan sababini aniqla.";
            case PAYMENT_DATE -> "Mijozdan aniq to'lov sanasini ol.";
            case CONFIRMATION -> "Kelishuvni takrorlab tasdiqla.";
            case CLOSING -> "Xushmuomala xayrlash.";
            case ESCALATE_TO_HUMAN -> "Operatorga o'tkazishni bildirib xayrlash.";
            case END_CALL -> "Qo'ng'iroqni yakunlash.";
        };
    }

    private static String allowedNext(DialogState state) {
        return switch (state) {
            case GREETING -> "IDENTITY_CHECK, END_CALL, ESCALATE_TO_HUMAN";
            case IDENTITY_CHECK -> "DEBT_NOTICE, END_CALL, ESCALATE_TO_HUMAN";
            case DEBT_NOTICE -> "REASON_INQUIRY, ESCALATE_TO_HUMAN, END_CALL";
            case REASON_INQUIRY -> "PAYMENT_DATE, ESCALATE_TO_HUMAN, END_CALL";
            case PAYMENT_DATE -> "CONFIRMATION, ESCALATE_TO_HUMAN, END_CALL";
            case CONFIRMATION -> "CLOSING, PAYMENT_DATE, ESCALATE_TO_HUMAN";
            case CLOSING -> "END_CALL";
            case ESCALATE_TO_HUMAN, END_CALL -> "(yakuniy holat)";
        };
    }

    private static String languageName(String bcp47) {
        if (bcp47 == null) {
            return "o'zbek";
        }
        return bcp47.startsWith("ru") ? "rus" : "o'zbek";
    }

    private static String orDash(Object value) {
        return value == null ? "—" : value.toString();
    }
}
