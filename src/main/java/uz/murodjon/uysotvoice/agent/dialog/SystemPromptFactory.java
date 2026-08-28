package uz.murodjon.uysotvoice.agent.dialog;

import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.agent.dialog.SentimentDetector.CustomerSentiment;
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
    static final List<String> PLATFORM_GUARDRAILS = List.of(
            "Mijozning shaxsiy ma'lumotlarini begona odamga aytma.",
            "Savolga javobni bilmasang — requestHumanTransfer bilan operatorga o'tkaz, o'ylab topma.",
            "Mijoz asabiylashsa yoki haqorat qilsa — darhol requestHumanTransfer chaqir.",
            "Faqat va faqat suhbatdosh ochiqchasiga o'zi boshqa odam ekanini yoki adashgan raqam ekanini aytsa (masalan: 'men u emasman', 'adashdingiz', 'bunaqa odam yo'q') — recordWrongPerson chaqiring. Mijoz 'alo', 'eshitaman', 'ha', 'kim bu?' desa yoki javobi tushunarsiz bo'lsa — darhol adashgan raqam deb hisoblamang, o'zingizni tanishtirib, ssenariy bo'yicha davom eting.",
            "Mijoz \"boshqa qo'ng'iroq qilmang\" desa — bahslashma, darhol recordDoNotCall chaqir va "
                    + "uzr so'rab xayrlash."
    );

    /** Uzbek label for a well-known fact name; falls back to the raw name otherwise. */
    static final Map<String, String> FACT_LABELS = Map.of(
            "clientName", "Ism",
            "debtAmount", "Summa",
            "currency", "Valyuta",
            "dueDate", "Muddat",
            "contractNumber", "Shartnoma raqami"
    );

    private final SentimentDetector sentimentDetector;

    public SystemPromptFactory() {
        this(new SentimentDetector());
    }

    public SystemPromptFactory(SentimentDetector sentimentDetector) {
        this.sentimentDetector = sentimentDetector != null ? sentimentDetector : new SentimentDetector();
    }

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

        // Multi-call memory and operator notes
        String prefName = strFact(c, "preferredName");
        String opNotes = strFact(c, "operatorNotes");
        String lastSummary = strFact(c, "lastCallSummary");
        if (prefName != null || opNotes != null || lastSummary != null) {
            sb.append("\nMULTI-CALL MEMORY & OPERATOR NOTES (Mijozning avvalgi suhbatlar xotirasi va eslatmalar):\n");
            if (prefName != null) {
                sb.append("- Mijozga qulay murojaat: ").append(prefName).append('\n');
            }
            if (opNotes != null) {
                sb.append("- Operator eslatmasi: ").append(opNotes).append('\n');
            }
            if (lastSummary != null) {
                sb.append("- Avvalgi qo'ng'iroq xulosasi: ").append(lastSummary).append('\n');
            }
        }

        if (s.isDisclosureSpoken()) {
            // The disclosure was already spoken from code (§11.1). Repeating it makes the
            // opening sound broken — and "do not repeat it" alone did not stop the model
            // opening with "Assalomu alaykum!" anyway. A negative rule leaves it to guess
            // what the first line should be instead, so say what it must look like.
            sb.append("\n[TIZIM: Salomlashuv va \"avtomatik xizmat, suhbat yozib olinmoqda\" ")
                    .append("ogohlantirishi allaqachon aytildi — mijoz ularni eshitib bo'ldi.\n")
                    .append("Shuning uchun birinchi javobingni salom bilan ham, o'zingni ")
                    .append("tanishtirish bilan ham BOSHLAMA (\"Assalomu alaykum\", \"Salom\", ")
                    .append("\"Men ... kompaniyasidanman\" — hech biri). Birinchi javobing ")
                    .append("to'g'ridan-to'g'ri ssenariyning navbatdagi bosqichiga o'tib, ish bilan boshlansin: ")
                    .append("suhbatdosh aynan o'sha odam ekanini so'ra yoki qarz xabarini yetkaz.]\n");
        } else {
            sb.append("\n[TIZIM: Birinchi javobingizda qisqa salomlashing, o'zingizni va kompaniyani tanishtiring ")
                    .append("hamda DARHOL ssenariy bo'yicha keyingi bosqichga (masalan: shaxsni tasdiqlash yoki qarz xabarini aytishga) ")
                    .append("o'ting (transitionTo chaqirib, gapni reply ga yozing). Shunchaki salomlashib to'xtab qolmang.]\n");
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

        sb.append("SSENARIY BO'YICHA HARAKAT: Kampaniyaning tanlangan ssenariysi bo'yicha bosqichma-bosqich ketma-ket harakat qiling. ")
                .append("Har bir bosqich maqsadini bajargach, transitionTo orqali keyingi ruxsat etilgan bosqichga o'ting va ")
                .append("o'sha bosqich talab qiladigan xabarni (masalan: qarz miqdori, muddati yoki to'lov sanasini kelishish) mijozga ayting. ")
                .append("Suhbatni sababsiz to'xtatib qo'ymang yoki yakunlamang.\n\n");

        sb.append("USLUB: qisqa, hurmatli, tabiiy jumlalar. Bir vaqtda bitta savol ber. ")
                .append("Ovozga aylantiriladi — qisqa gaplar tuz, ro'yxat yoki maxsus belgilar ishlatma.\n");
        // What makes a call sound scripted is less the voice than the turn shape: every
        // reply a complete formal paragraph that ignores what the caller just said. So
        // ask for the spoken register a human agent uses — react first, written-official
        // vocabulary out, acknowledgment before argument. The example words follow the
        // call's language, or the model would drop Uzbek back-channels into a Russian
        // call. Lives in the cached prefix like the other style rules: billed once per
        // call, not per turn.
        boolean russian = isRussian(s.language());
        sb.append("INSONDEK GAPIR: mijoz gapiga avval bir og'iz munosabat bildir ")
                .append(russian
                        ? "(\"Хорошо\", \"Понятно\", \"Да, конечно\")"
                        : "(\"Xo'p\", \"Tushunarli\", \"Yaxshi\")")
                .append(", keyin davom et — lekin har safar har xil, bitta so'zni qayta-qayta ")
                .append("ishlatsang robotga o'xshaysan. Yozma-rasmiy iboralar ")
                .append(russian
                        ? "(\"данный\", \"осуществлять\", \"уважаемый клиент\")"
                        : "(\"ushbu\", \"mazkur\", \"amalga oshirish\", \"hurmatli mijoz\")")
                .append(" o'rniga og'zaki so'zlashuvdagi oddiy shaklni tanla. Qisqa savolga qisqa ")
                .append("javob ber — hammasini bir javobda tushuntirishga urinma. Mijoz xavotir yoki ")
                .append("norozilik bildirsa, avval uni qisqa tan ol ")
                .append(russian ? "(\"Понимаю вас\")" : "(\"Tushunaman, noqulay vaziyat\")")
                .append(", keyin ishga qayt. Javoblaring anketa savol-javobi emas, tabiiy suhbat bo'lsin.\n");
        if (!russian) {
            // Real calls produced "1500000 so me'doridagi" for "so'm miqdoridagi" and
            // "kuninigiz" for "kuningiz". A mangled word is not a spelling problem here —
            // it goes straight to TTS and the caller hears the mangling.
            sb.append("TIL: o'zbek adabiy tilida to'g'ri yoz. o' va g' harflarini doim apostrof ")
                    .append("bilan yoz (so'm, to'lov, bo'yicha, o'tgan, kuningiz). So'zni bo'lib ")
                    .append("yuborma va harfini tushirib qoldirma — buzuq yozilgan so'z ovozda ham ")
                    .append("buzuq eshitiladi.\n");
        }
        // The TTS layer writes digits out in Uzbek words (SpeechTextNormalizer), and the
        // fact guard compares digits against the facts. Both only work on digits, so the
        // model must not spell a sum out itself.
        sb.append("Summa, sana va raqamlarni FAKTLARdagidek raqam bilan yoz ")
                .append("(\"1500000 so'm\", \"2026-yil 1-iyul\") — so'z bilan yozma, ")
                .append("ovozga aylantirilganda o'zi to'g'ri o'qiladi.\n");
        // Observed on real calls: the caller said only "allo" and the bot answered by
        // delivering the whole debt notice again — contract number, sum and due date. The
        // stage purpose in turnAnnex is re-sent every turn and reads as an order to state
        // it once more, so the counter-rule has to be spelled out. It lives here, in the
        // cached prefix, rather than in the annex: the annex is billed on every turn.
        sb.append("TAKRORLAMA: suhbatda allaqachon aytgan faktingni (shartnoma raqami, summa, ")
                .append("muddat) qayta aytma. Mijoz eshitmagan yoki tushunmagan bo'lsa — butun ")
                .append("xabarni emas, faqat so'ralgan qismini qisqa ayt. Har bir javobing ")
                .append("suhbatning davomi bo'lsin, uni boshidan boshlash emas. Istisno: ")
                .append("kelishilgan sana va summani yakunda bir marta tasdiqlab o'tish kerak.\n");
        // Stage purpose arrives every turn; without this it reads as a fresh instruction
        // to carry the stage out again, however far into the stage the conversation is.
        sb.append("Quyida har bir qadamda keladigan matn — o'sha bosqichning umumiy MAQSADI, ")
                .append("har bir javob uchun buyruq emas. Maqsadni allaqachon bajargan bo'lsang, ")
                .append("takrorlama — suhbatni keyingi bosqichga o'tkazib davom ettir.\n");
        // Asking for plain text alongside the tool call (so it could be streamed and
        // spoken sentence by sentence) was tried and reverted — Gemini answered a
        // tool-calling turn with neither, and the retry round trip cost more than the
        // streaming saved. The line goes in the tool argument because a required argument
        // is the only part of this the model reliably fills. See DialogTools.
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
        if (s.lastCustomerSentiment() != null && s.lastCustomerSentiment() != CustomerSentiment.NEUTRAL) {
            String directive = sentimentDetector.empathyDirective(s.lastCustomerSentiment(), s.language());
            if (directive != null && !directive.isBlank()) {
                sb.append('\n').append(directive);
            }
        }
        return sb.toString();
    }

    static StageDef stageOf(ScenarioDefinition def, String stageId) {
        if (def == null || def.stages() == null) {
            return null;
        }
        return def.stages().stream()
                .filter(st -> st.id().equals(stageId))
                .findFirst()
                .orElse(null);
    }

    private static String allowedNext(StageDef stage) {
        if (stage == null || stage.allowedTransitions() == null || stage.allowedTransitions().isEmpty()) {
            return "(yakuniy holat)";
        }
        return String.join(", ", stage.allowedTransitions());
    }

    private static String languageName(String bcp47) {
        if (bcp47 == null) {
            return "o'zbek";
        }
        String lower = bcp47.toLowerCase();
        if (lower.startsWith("ru")) {
            return "rus";
        }
        if (lower.startsWith("en")) {
            return "ingliz";
        }
        return "o'zbek";
    }

    private static boolean isRussian(String bcp47) {
        return bcp47 != null && bcp47.toLowerCase().startsWith("ru");
    }

    private static String weekdayUz(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY -> "dushanba";
            case TUESDAY -> "seshanba";
            case WEDNESDAY -> "chorshanba";
            case THURSDAY -> "payshanba";
            case FRIDAY -> "juma";
            case SATURDAY -> "shanba";
            case SUNDAY -> "yakshanba";
        };
    }

    private static String orDash(Object s) {
        return s != null && !s.toString().isBlank() ? s.toString() : "—";
    }

    private static String strFact(CallContext c, String key) {
        Object v = c.fact(key);
        return v != null && !v.toString().isBlank() ? v.toString() : null;
    }
}
