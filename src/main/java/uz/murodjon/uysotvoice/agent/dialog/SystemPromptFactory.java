package uz.murodjon.uysotvoice.agent.dialog;

import org.springframework.stereotype.Component;
import uz.murodjon.uysotvoice.shared.dialog.DialogState;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Builds the per-turn prompt from a session's facts and FSM state (PROJECT.md §4.1,
 * §4.4).
 *
 * <p>Deliberately split in two. {@link #stablePrefix} is everything that cannot change
 * during a call — role, today's date, the debtor's facts, the guardrails, the style
 * rules — and is built once per call and reused verbatim as the system message.
 * {@link #turnAnnex} is the part that moves with the FSM (current state, its objective,
 * the allowed transitions, a barge-in note) and is appended <em>after</em> the chat
 * history as a transient system aside.
 *
 * <p>The split is what makes the request cacheable. Gemini's implicit context caching
 * only pays off when consecutive requests share a byte-identical <em>prefix</em>; with
 * the state block sitting in the middle of the system message, every transition
 * invalidated the whole conversation and each turn was billed at full price. Keeping
 * the prefix append-only — stable system message, then the growing history — means a
 * turn only pays full rate for what is genuinely new. Putting the state last also puts
 * it closest to the generation point, which is where models follow instructions best.
 */
@Component
public class SystemPromptFactory {

    /**
     * The unchanging half of the prompt: who the agent is, the facts it may state, and
     * the rules it must not break. Cache this per call ({@link DialogSession#systemPrefix}) —
     * rebuilding it produces the same string and only risks breaking the cached prefix
     * (e.g. across midnight).
     */
    public String stablePrefix(DialogSession s) {
        CallContext c = s.context();
        StringBuilder sb = new StringBuilder();

        sb.append("Siz \"Uysot\" kompaniyasining avtomatik qarz undirish ovozli agentisiz. ")
                .append("Telefon orqali mijoz bilan ").append(languageName(s.language()))
                .append(" tilida tabiiy suhbatlashasiz.\n\n");

        // Without today's date the model invents a year for "kelasi oyning 5-sanasi",
        // and recordPaymentPromise then rejects it as a past date (§4.4 guardrail).
        LocalDate today = LocalDate.now();
        sb.append("BUGUNGI SANA: ").append(today).append(" (").append(weekdayUz(today.getDayOfWeek()))
                .append(").\n\n");

        sb.append("MIJOZ MA'LUMOTLARI (FAKTLAR — faqat shu raqamlarni ayting, o'zgartirmang):\n");
        sb.append("- Ism: ").append(orDash(c.clientName())).append('\n');
        sb.append("- Qarz summasi: ").append(orDash(c.debtAmount()))
                .append(c.currency() != null ? " " + c.currency() : "").append('\n');
        sb.append("- Dastlabki to'lov muddati: ").append(orDash(c.dueDate())).append('\n');
        sb.append("- Shartnoma raqami: ").append(orDash(c.contractNumber())).append('\n');
        if (c.goal() != null && !c.goal().isBlank()) {
            sb.append("- Kampaniya maqsadi: ").append(c.goal()).append('\n');
        }

        if (s.isDisclosureSpoken()) {
            // The disclosure was already spoken from code (§11.1). Repeating it makes
            // the opening sound broken.
            sb.append("\n[TIZIM: Salomlashuv va \"avtomatik xizmat, suhbat yozib olinmoqda\" ")
                    .append("ogohlantirishi allaqachon aytildi. Ularni TAKRORLAMA — to'g'ridan-to'g'ri ")
                    .append("ishga o't.]\n");
        }

        sb.append("\nQAT'IY QOIDALAR:\n");
        sb.append("- Qarz summasini HECH QACHON o'zgartirma. Faqat berilgan raqamni ayt.\n");
        sb.append("- Chegirma, imtiyoz yoki qarz kechirishni HECH QACHON taklif qilma.\n");
        sb.append("- To'lov muddatini o'zing uzaytirma — faqat mijoz aytgan sanani yozib ol.\n");
        sb.append("- Mijoz nisbiy sana aytsa (\"ertaga\", \"dushanba\", \"kelasi oyning 5-sanasi\") — uni ")
                .append("BUGUNGI SANAdan hisoblab yyyy-MM-dd ko'rinishida recordPaymentPromise'ga ber. ")
                .append("Yilni o'zingdan to'qima.\n");
        sb.append("- Huquqiy oqibatlar, sud, jarima yoki ijro haqida o'zingdan gapirma, qo'rqitma.\n");
        sb.append("- Mijozning shaxsiy ma'lumotlarini begona odamga (qarzdor bo'lmagan kishiga) aytma.\n");
        sb.append("- Savolga javobni bilmasang — requestHumanTransfer bilan operatorga o'tkaz, o'ylab topma.\n");
        sb.append("- Mijoz asabiylashsa yoki haqorat qilsa — darhol requestHumanTransfer chaqir.\n");
        sb.append("- Telefondagi odam qarzdor emas bo'lsa — recordWrongPerson chaqir.\n");
        sb.append("- Mijoz \"boshqa qo'ng'iroq qilmang\" desa — bahslashma, darhol ")
                .append("recordDoNotCall chaqir va uzr so'rab xayrlash.\n\n");

        sb.append("USLUB: qisqa, hurmatli, tabiiy jumlalar. Bir vaqtda bitta savol ber. ")
                .append("Ovozga aylantiriladi — qisqa gaplar tuz, ro'yxat yoki maxsus belgilar ishlatma.\n");
        sb.append("HAR BIR javobing mijozga ovoz bilan aytiladigan matn bo'lishi SHART — matnsiz javob ")
                .append("qaytarma. Tool chaqirish (bosqich o'tkazish, va'da/sabab yozish, yakunlash) matnning ")
                .append("o'rnini bosmaydi: kerakli tool'ni chaqir VA aytadigan gapingni ham yoz.");

        return sb.toString();
    }

    /**
     * The moving half: where the FSM is now and where it may go next. Sent after the
     * history as a transient aside — never stored in it, or the history would fill up
     * with stale state blocks and stop being an append-only (cacheable) prefix.
     */
    public String turnAnnex(DialogSession s) {
        StringBuilder sb = new StringBuilder();
        sb.append("[TIZIM: JORIY BOSQICH: ").append(s.state()).append(" — ").append(objective(s.state())).append('\n');
        sb.append("Ruxsat etilgan keyingi bosqichlar: ").append(allowedNext(s.state())).append('\n');
        sb.append("Bosqichni o'zgartirish kerak bo'lsa transitionTo tool'ini chaqiring.]");
        if (s.isInterrupted()) {
            // Tell the model it was cut off and where it stopped (§7.2 step 5).
            sb.append("\n[TIZIM: Mijoz siz gapirayotganda sizni bo'ldi. Siz shu yergacha aytgan edingiz: \"")
                    .append(s.lastAgentText() == null ? "" : s.lastAgentText())
                    .append("\". Mijozning gapiga moslashing; butun gapni qaytadan boshlamang.]");
        }
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

    /** Weekday in Uzbek — the JVM has no reliable uz locale, and "dushanba" is what a caller says. */
    private static String weekdayUz(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "dushanba";
            case TUESDAY -> "seshanba";
            case WEDNESDAY -> "chorshanba";
            case THURSDAY -> "payshanba";
            case FRIDAY -> "juma";
            case SATURDAY -> "shanba";
            case SUNDAY -> "yakshanba";
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
