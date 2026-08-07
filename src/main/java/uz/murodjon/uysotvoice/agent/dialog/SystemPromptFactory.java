package uz.murodjon.uysotvoice.agent.dialog;

import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.scenario.dto.FactField;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioDefinition;
import uz.murodjon.uysotvoice.scenario.dto.StageDef;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Builds the per-turn prompt from a session's bound scenario, facts and FSM stage
 * (PROJECT.md §4.1, §4.4, ROADMAP A.3).
 *
 * <p>Deliberately split in two. {@link #stablePrefix} is everything that cannot change
 * during a call — role, today's date, the facts, the guardrails, the style rules — and
 * is built once per call and reused verbatim as the system message. {@link #turnAnnex}
 * is the part that moves with the FSM (current stage, its purpose, the allowed
 * transitions, a barge-in note) and is appended <em>after</em> the chat history as a
 * transient system aside.
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
     * Platform-level rules that apply to every scenario regardless of what it declares
     * (ROADMAP A.3/§5.1): a custom scenario's own {@code guardrails} cannot remove or
     * soften these. Domain-specific rules (e.g. "never change the debt amount") belong
     * in the scenario's own {@code guardrails} instead — see the {@code debt-collection}
     * seed for an example.
     */
    private static final List<String> PLATFORM_GUARDRAILS = List.of(
            "Mijozning shaxsiy ma'lumotlarini begona odamga aytma.",
            "Savolga javobni bilmasang — requestHumanTransfer bilan operatorga o'tkaz, o'ylab topma.",
            "Mijoz asabiylashsa yoki haqorat qilsa — darhol requestHumanTransfer chaqir.",
            "Suhbatdosh bu qo'ng'iroqning haqiqiy manzili emasligi aniqlansa — recordWrongPerson chaqiring.",
            "Mijoz \"boshqa qo'ng'iroq qilmang\" desa — bahslashma, darhol recordDoNotCall chaqir va "
                    + "uzr so'rab xayrlash."
    );

    /** Uzbek label for a well-known fact name; falls back to the raw name otherwise. */
    private static final Map<String, String> FACT_LABELS = Map.of(
            "clientName", "Ism",
            "debtAmount", "Summa",
            "currency", "Valyuta",
            "dueDate", "Muddat",
            "contractNumber", "Shartnoma raqami"
    );

    /**
     * The unchanging half of the prompt: who the agent is, the facts it may state, and
     * the rules it must not break. Cache this per call ({@link DialogSession#systemPrefix()}) —
     * rebuilding it produces the same string and only risks breaking the cached prefix
     * (e.g. across midnight).
     */
    public String stablePrefix(DialogSession s) {
        ScenarioDefinition def = s.scenario();
        CallContext c = s.context();
        StringBuilder sb = new StringBuilder();

        sb.append(def.rolePrompt()).append(' ')
                .append("Telefon orqali mijoz bilan ").append(languageName(s.language()))
                .append(" tilida tabiiy suhbatlashasiz.\n\n");

        // Which company the call is actually from, overriding whatever name the scenario's
        // rolePrompt happens to carry. A built-in template is cloned by every tenant, so a
        // company name written into it belongs to whoever wrote the template — and the
        // disclosure spoken from code (§11.1) already names the real one. Hearing two
        // different companies in one call is worse than either name on its own.
        if (s.companyName() != null && !s.companyName().isBlank()) {
            sb.append("KOMPANIYA: siz \"").append(s.companyName().trim())
                    .append("\" kompaniyasi nomidan qo'ng'iroq qilyapsiz. O'zingizni tanishtirganda ")
                    .append("faqat shu nomni ayting — yuqoridagi matnda boshqa nom bo'lsa ham.\n\n");
        }

        // Without today's date the model invents a year for "kelasi oyning 5-sanasi",
        // and recordPaymentPromise then rejects it as a past date (§4.4 guardrail).
        LocalDate today = LocalDate.now();
        sb.append("BUGUNGI SANA: ").append(today).append(" (").append(weekdayUz(today.getDayOfWeek()))
                .append(").\n\n");

        List<FactField> factSchema = def.factSchema();
        if (factSchema != null && !factSchema.isEmpty()) {
            sb.append("FAKTLAR (faqat shu ma'lumotlarni ayting, o'zgartirmang):\n");
            for (FactField f : factSchema) {
                sb.append("- ").append(FACT_LABELS.getOrDefault(f.name(), f.name())).append(": ")
                        .append(orDash(c.fact(f.name()))).append('\n');
            }
        }
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
        List<String> scenarioGuardrails = def.guardrails();
        if (scenarioGuardrails != null) {
            for (String rule : scenarioGuardrails) {
                sb.append("- ").append(rule).append('\n');
            }
        }
        for (String rule : PLATFORM_GUARDRAILS) {
            sb.append("- ").append(rule).append('\n');
        }
        sb.append('\n');

        sb.append("USLUB: qisqa, hurmatli, tabiiy jumlalar. Bir vaqtda bitta savol ber. ")
                .append("Ovozga aylantiriladi — qisqa gaplar tuz, ro'yxat yoki maxsus belgilar ishlatma.\n");
        // The TTS layer writes digits out in Uzbek words (SpeechTextNormalizer), and the
        // fact guard compares digits against the facts. Both only work on digits, so the
        // model must not spell a sum out itself.
        sb.append("Summa, sana va raqamlarni FAKTLARdagidek raqam bilan yoz ")
                .append("(masalan \"1500000 so'm\", \"2026-yil 1-iyul\") — so'z bilan yozma, ")
                .append("ovozga aylantirilganda o'zi to'g'ri o'qiladi.\n");
        sb.append("HAR BIR javobing mijozga ovoz bilan aytiladigan matn bo'lishi SHART — matnsiz javob ")
                .append("qaytarma. Tool chaqirsang (bosqich o'tkazish, va'da/sabab yozish, yakunlash), ")
                .append("aytadigan gapingni o'sha tool'ning \"reply\" parametriga yoz — mijoz aynan shuni ")
                .append("eshitadi. \"reply\" ni bo'sh qoldirma.");

        return sb.toString();
    }

    /**
     * The moving half: where the FSM is now and where it may go next. Sent after the
     * history as a transient aside — never stored in it, or the history would fill up
     * with stale state blocks and stop being an append-only (cacheable) prefix.
     *
     * <p>The "a tool call does not replace the spoken line" rule is repeated here even
     * though {@link #stablePrefix} already states it. That is deliberate: the prefix sits
     * tens of turns back by mid-call, and the rule is broken precisely on the turns this
     * block is about — a stage transition. A model that calls {@code transitionTo} and
     * says nothing costs {@code DialogEngine.streamTurn} a whole second LLM round trip
     * inside the turnaround budget, so the reminder belongs next to the instruction that
     * provokes it.
     */
    public String turnAnnex(DialogSession s) {
        StageDef stage = stageOf(s.scenario(), s.state());
        StringBuilder sb = new StringBuilder();
        sb.append("[TIZIM: JORIY BOSQICH: ").append(s.state()).append(" — ")
                .append(stage != null ? stage.purpose() : "").append('\n');
        sb.append("Ruxsat etilgan keyingi bosqichlar: ").append(allowedNext(stage)).append('\n');
        sb.append("Bosqichni o'zgartirish kerak bo'lsa transitionTo tool'ini chaqiring va mijozga ")
                .append("aytadigan gapingizni uning \"reply\" parametriga yozing — u bo'sh bo'lsa ")
                .append("mijoz jimlikni eshitadi.]");
        if (s.isInterrupted()) {
            // Tell the model it was cut off and where it stopped (§7.2 step 5).
            sb.append("\n[TIZIM: Mijoz siz gapirayotganda sizni bo'ldi. Siz shu yergacha aytgan edingiz: \"")
                    .append(s.lastAgentText() == null ? "" : s.lastAgentText())
                    .append("\". Mijozning gapiga moslashing; butun gapni qaytadan boshlamang.]");
        }
        return sb.toString();
    }

    /** The stage a given id names, or {@code null} if the scenario has none by that id. */
    static StageDef stageOf(ScenarioDefinition def, String stageId) {
        if (def.stages() == null) {
            return null;
        }
        return def.stages().stream().filter(st -> st.id().equals(stageId)).findFirst().orElse(null);
    }

    private static String allowedNext(StageDef stage) {
        if (stage == null || stage.allowedTransitions() == null || stage.allowedTransitions().isEmpty()) {
            return "(yakuniy holat)";
        }
        return String.join(", ", stage.allowedTransitions());
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
