package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.agent.dialog.SentimentDetector.CustomerSentiment;
import uz.murodjon.robotcallv2.scenario.domain.entity.FactField;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;

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
            "Faqat va faqat qarshingizdagi shaxs ochiqchasiga o'zi boshqa odam ekanini yoki adashgan raqam ekanini aytsa (masalan: 'men u emasman', 'adashdingiz', 'bunaqa odam yo'q') — recordWrongPerson chaqiring. Mijoz 'alo', 'eshitaman', 'ha', 'kim bu?' desa yoki javobi tushunarsiz bo'lsa — darhol adashgan raqam deb hisoblamang, o'zingizni tanishtirib, ssenariy bo'yicha davom eting.",
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

    public static StageDef stageOf(ScenarioDefinition def, String stageId) {
        if (def == null) {
            return null;
        }
        return def.findStage(stageId);
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

        if (s.companyName() != null && !s.companyName().isBlank()) {
            sb.append("KOMPANIYA: siz \"").append(s.companyName().trim())
                    .append("\" kompaniyasi nomidan qo'ng'iroq qilyapsiz. O'zingizni tanishtirganda ")
                    .append("faqat shu nomni ayting — yuqoridagi matnda boshqa nom bo'lsa ham.\n\n");
        }

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
            sb.append("\n[TIZIM: Salomlashuv va \"avtomatik xizmat, suhbat yozib olinmoqda\" ")
                    .append("ogohlantirishi allaqachon aytildi — mijoz ularni eshitib bo'ldi.\n")
                    .append("Shuning uchun birinchi javobingni salom bilan ham, o'zingni ")
                    .append("tanishtirish bilan ham BOSHLAMA (\"Assalomu alaykum\", \"Salom\", ")
                    .append("\"Men ... kompaniyasidanman\" — hech biri). Birinchi javobing ")
                    .append("to'g'ridan-to'g'ri ssenariyning navbatdagi bosqichiga o'tib, jonli, tabiiy insondek boshlansin: ")
                    .append("shaxsni tasdiqlashda xuddi tajribali tirik operator kabi \"Men Murodjon aka bilan gaplashayapmanmi?\" yoki \"[Ism] aka, sizmisiz?\" deb so'ra ")
                    .append("(QAT'IYAN TAQIQLANADI: \"siz [Ism]misiz?\", \"suhbatdoshim\", \"suhbatdosh\" yoki \"mijoz\" deb aytish). Agar shaxsni so'rash kerak bo'lmasa, muloyimlik bilan maqsadga o't.]\n");
        } else {
            sb.append("\n[TIZIM: Birinchi javobingizda qisqa salomlashing, o'zingizni va kompaniyani tanishtiring ")
                    .append("hamda DARHOL ssenariy bo'yicha keyingi bosqichga (masalan: shaxsni tasdiqlash uchun \"Men Murodjon aka bilan gaplashayapmanmi?\" yoki asosiy maqsadga) ")
                    .append("o'ting (transitionTo chaqirib, gapni reply ga yozing). Shunchaki salomlashib to'xtab qolmang. QAT'IYAN TAQIQLANADI: \"Siz [Ism]misiz?\" deb so'rash.]\n");
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
        // Spoken human dialogue style rules
        boolean russian = isRussian(s.language());
        sb.append("INSONDEK GAPIR: mijoz gapiga avval bir og'iz munosabat bildir ")
                .append(russian
                        ? "(\"Хорошо\", \"Понятно\", \"Да, конечно\")"
                        : "(\"Xo'p\", \"Tushunarli\", \"Yaxshi\", \"Aha\")")
                .append(", keyin davom et — lekin har safar har xil, bitta so'zni qayta-qayta ")
                .append("ishlatsang robotga o'xshaysan. Yozma-rasmiy yoki tarjima qilingan sun'iy iboralar ")
                .append(russian
                        ? "(\"данный\", \"осуществлять\", \"уважаемый клиент\", \"собеседник\", \"мой собеседник\")"
                        : "(\"ushbu\", \"mazkur\", \"amalga oshirish\", \"hurmatli mijoz\", \"hurmatli fuqaro\", \"suhbatdosh\", \"suhbatdoshim\")")
                .append(" o'rniga og'zaki so'zlashuvdagi jonli va tabiiy shaklni tanla. Qisqa savolga qisqa ")
                .append("javob ber — hammasini bir javobda tushuntirishga urinma. Mijoz xavotir yoki ")
                .append("norozilik bildirsa, avval uni qisqa tan ol ")
                .append(russian ? "(\"Понимаю вас\")" : "(\"Tushunaman, noqulay vaziyat\")")
                .append(", keyin ishga qayt. Javoblaring anketa savol-javobi emas, tabiiy jonli suhbat bo'lsin.\n");
        if (!russian) {
            sb.append("MUROJAAT VA SHAXSNI ANIQLASH (QAT'IY): Mijozga HECH QACHON \"suhbatdoshim\", \"suhbatdosh\", \"mijoz\" deb murojaat qilma. \n")
                    .append("QAT'IYAN TAQIQLANGAN: \"Siz [Ism]misiz?\", \"Siz [Ism] bo'lasizmi?\", \"Siz falonchimisiz?\" deb so'rash. Bu juda qo'pol va robotdek eshitiladi. \n")
                    .append("TO'G'RI SHAKL: Shaxsni aniqlash bosqichida xuddi haqiqiy tirik operator kabi faqat muloyim va tabiiy jumlalarni ishlat: ")
                    .append("\"Men Murodjon aka bilan gaplashayapmanmi?\" yoki \"[Ism] aka, sizmisiz?\" (ayol kishi bo'lsa \"opa\", erkak kishi bo'lsa \"aka\" qo'sh). \n")
                    .append("Ism bilan murojaat qilganda o'zbekona hurmat bilan faqat ismiga \"aka\"/\"opa\" qo'shib gapir (masalan: \"Murodjon aka\", \"Shahnoza opa\") — ")
                    .append("hech qachon familiya yoki to'liq ism-sharifni aytma.\n");
            // Replacements for dry official phrases
            sb.append("OG'ZAKI SHAKL: \"to'lovni amalga oshirasiz\" emas — \"to'laysiz\"; ")
                    .append("\"qarzdorligingiz mavjud\" emas — \"qarzingiz bor ekan\"; ")
                    .append("\"ma'lumot beraman\" emas — \"aytaman\"; ")
                    .append("\"to'lanishi kerak bo'lgan summa\" emas — \"qarz\".\n");
            sb.append("TIL: o'zbek adabiy tilida to'g'ri yoz. o' va g' harflarini doim apostrof ")
                    .append("bilan yoz (so'm, to'lov, bo'yicha, o'tgan, kuningiz). So'zni bo'lib ")
                    .append("yuborma va harfini tushirib qoldirma — buzuq yozilgan so'z ovozda ham ")
                    .append("buzuq eshitiladi.\n");
        }
        sb.append("Summa, sana va raqamlarni FAKTLARdagidek raqam bilan yoz ")
                .append("(\"1500000 so'm\", \"1-iyul\") — so'z bilan yozma, ")
                .append("ovozga aylantirilganda o'zi to'g'ri o'qiladi.\n");
        sb.append("SANA: joriy yildagi sanada yilni aytma — \"3-sentabr\" yetarli, ")
                .append("\"2026-yil 3-sentabr kuni\" emas. Mijoz \"ertaga\", \"dushanba\" desa, ")
                .append("tasdiqlaganda ham o'sha tabiiy shaklni saqla (\"ertaga, 3-sentabrda\"); ")
                .append("to'liq yyyy-MM-dd sana faqat tool parametriga yoziladi.\n");
        sb.append("QISQA GAP: bir javobda ko'pi bilan ikki-uch qisqa gap va bitta savol. ")
                .append("Shartnoma, summa va muddatni bitta uzun gapga tiqma — alohida gaplarga ")
                .append("bo'l. O'zingni va kompaniyani bir marta tanishtirasan, keyingi ")
                .append("javoblarda kompaniya nomini qayta aytma.\n");
        sb.append("TAKRORLAMA: suhbatda allaqachon aytgan faktingni (shartnoma raqami, summa, ")
                .append("muddat) qayta aytma. Mijoz eshitmagan yoki tushunmagan bo'lsa — butun ")
                .append("xabarni emas, faqat so'ralgan qismini qisqa ayt. Har bir javobing ")
                .append("suhbatning davomi bo'lsin, uni boshidan boshlash emas. Istisno: ")
                .append("kelishilgan sana va summani yakunda bir marta tasdiqlab o'tish kerak.\n");
        sb.append("Quyida har bir qadamda keladigan matn — o'sha bosqichning umumiy MAQSADI, ")
                .append("har bir javob uchun buyruq emas. Maqsadni allaqachon bajargan bo'lsang, ")
                .append("takrorlama — suhbatni keyingi bosqichga o'tkazib davom ettir.\n");

        return sb.toString();
    }

    /**
     * The dynamic tail of the prompt: moves with the FSM, and is appended to the message
     * sequence as a system aside rather than baked into {@link #stablePrefix}.
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
            sb.append("\n[TIZIM: Mijoz siz gapirayotganda sizni bo'ldi. Siz shu yergacha aytgan edingiz: \"")
                    .append(s.lastAgentText() == null ? "" : s.lastAgentText())
                    .append("\". Mijozning gapiga moslashing; butun gapni qaytadan boshlamang.]");
        }
        if (s.lastCustomerSentiment() != null && s.lastCustomerSentiment() != CustomerSentiment.NEUTRAL) {
            String directive = sentimentDetector.empathyDirective(s.lastCustomerSentiment(), s.language());
            if (directive != null && !directive.isBlank()) {
                sb.append(directive);
            }
        }
        return sb.toString();
    }

    private static String allowedNext(StageDef stage) {
        if (stage == null || stage.allowedTransitions() == null || stage.allowedTransitions().isEmpty()) {
            return "(yakuniy holat)";
        }
        return String.join(", ", stage.allowedTransitions());
    }

    private static String languageName(String code) {
        if (isRussian(code)) return "rus";
        if (code != null && code.toLowerCase().startsWith("en")) return "ingliz";
        return "o'zbek";
    }

    private static boolean isRussian(String code) {
        return code != null && code.toLowerCase().startsWith("ru");
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

    private static String orDash(Object value) {
        return value == null ? "—" : String.valueOf(value);
    }

    private static String strFact(CallContext c, String key) {
        Object val = c.fact(key);
        if (val instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        return null;
    }
}
