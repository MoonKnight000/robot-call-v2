package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.agent.dialog.SentimentDetector.CustomerSentiment;
import uz.murodjon.robotcallv2.scenario.domain.entity.FactField;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
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
     * How every note addressed to the model rather than to the caller opens. The model is
     * supposed to act on these, never to repeat them — a reply that carries the marker is
     * an echo of the annex and must not reach the caller ({@link #isSystemNote}).
     */
    public static final String SYSTEM_NOTE_MARKER = "[TIZIM";

    /**
     * Whether {@code text} is the model reciting a note meant for it. Caught here rather
     * than prompted away, because the one time it happened the caller heard "TIZIM: JORIY
     * BOSQICH: IDENTITY_CHECK" read out loud.
     */
    public static boolean isSystemNote(String text) {
        return text != null && text.contains(SYSTEM_NOTE_MARKER);
    }

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
            "contractNumber", "Shartnoma raqami",
            "penaltyAmount", "Peniya",
            "contractCancelDays", "Shartnoma bekor bo'lishiga qolgan kun"
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

        sb.append("SSENARIY: bosqichma-bosqich boring. Bosqich maqsadi bajarilgach transitionTo bilan ")
                .append("keyingi ruxsat etilgan bosqichga o'ting va o'sha bosqich talab qilgan gapni ayting ")
                .append("(qarz miqdori, muddati, to'lov sanasini kelishish). Suhbatni sababsiz yakunlamang. ")
                .append("Har bosqichning matni — umumiy MAQSAD, har bir javob uchun buyruq emas: maqsadni ")
                .append("bajargan bo'lsangiz takrorlamang, keyingi bosqichga o'ting.\n\n");

        // Numbered, one line each, and last in the prefix. The same rules as prose in one
        // paragraph were being dropped: a real call opened with "Ushbu to'lov qachon amalga
        // oshirilishi kutilmoqda?", two banned words in a sentence whose form was banned too.
        sb.append("GAPIRISH QOIDALARI — har bir javobdan oldin shu ro'yxatdan o'tkazing:\n");
        int n = 1;
        for (String rule : speechRules(isRussian(s.language()))) {
            sb.append(n++).append(". ").append(rule).append('\n');
        }
        sb.append('\n').append(examples(isRussian(s.language()))).append('\n');

        return sb.toString();
    }

    /**
     * The style rules, one line each. Numbered by the caller, so the uz-only entries can
     * drop out of a Russian call without leaving a hole in the list.
     */
    private static List<String> speechRules(boolean russian) {
        List<String> rules = new ArrayList<>();
        rules.add("Hech qachon inglizcha texnik so'zlar, kodlar, o'zgaruvchi nomlari yoki placeholderlar "
                + "(masalan: dynamic_thought_or_fallback, thought, undefined) ishlatmang. Faqat jonli "
                + (russian ? "ruscha" : "o'zbekcha") + " gaplar bilan gapiring.");
        rules.add("Ko'pi bilan ikki qisqa gap va bitta savol. Shartnoma, summa va muddatni bitta "
                + "uzun gapga tiqmang — alohida gaplarga bo'ling.");
        rules.add("Har bir javobingiz bitta savol bilan tugasin, va u FAQAT ishni oldinga "
                + "suradigan savol bo'lsin "
                + (russian ? "(\"почему не оплачено?\", \"когда сможете оплатить?\")" : "(\"nima uchun to'lanmayapti?\", \"qachon to'lay olasiz?\")")
                + " — ya'ni javobi sizga kerak bo'lgan savol. Bo'sh, tasdiqlovchi savollar "
                + "QAT'IYAN TAQIQLANADI: "
                + (russian ? "\"вы в курсе?\", \"слышите меня?\", \"вы меня понимаете?\"" : "\"bu haqda xabaringiz bormidi?\", \"eshitib turibsizmi?\", \"tushundingizmi?\"")
                + " — ular suhbatni bir qadam ham oldinga surmaydi, mijozga esa sun'iy "
                + "eshitiladi. Faktni aytdingizmi — darhol keyingi kerakli savolga o'ting. "
                + "Istisnolar: kelishuvni yakunda bir marta tasdiqlash va endCall bilan "
                + "xayrlashish.");
        rules.add("Mijoz mazmunli gap aytsa (sabab, e'tiroz, sana, savol) — javobingizni bir og'iz "
                + "munosabat bilan boshlang "
                + (russian ? "(\"Хорошо\", \"Понятно\", \"Да, конечно\")" : "(\"Xo'p\", \"Tushunarli\", \"Yaxshi\", \"Aha\")")
                + ", lekin ketma-ket ikki javobda bir xilini takrorlamang. Mijoz shunchaki tasdiqlasa "
                + (russian ? "(\"да\", \"ага\", \"хорошо\")" : "(\"ha\", \"aha\", \"xo'p\", \"shunday\")")
                + " — munosabat bildirmang, to'g'ridan-to'g'ri ishga o'ting: tasdiqni qayta "
                + "tasdiqlash robotdek eshitiladi. Munosabat BIR SO'Z bo'lsin — hissiyot "
                + "bildiruvchi alohida gap qo'shmang "
                + (russian ? "(\"я вас прекрасно понимаю\", \"мне очень жаль\")" : "(\"vaziyatni to'g'ri tushunaman\", \"sizni juda yaxshi tushunib turibman\", \"juda afsusdaman\")")
                + ": o'ynalgan hamdardlik samimiy emas, sun'iy eshitiladi.");
        rules.add("Allaqachon aytgan faktingizni (shartnoma raqami, summa, muddat) qayta aytmang va "
                + "allaqachon bergan savolingizni boshqa so'z bilan qayta bermang. Mijoz eshitmagan "
                + "bo'lsa — butun xabarni emas, faqat so'ralgan qismini qisqa ayting. Istisno: "
                + "kelishilgan sana va summa yakunda bir marta tasdiqlanadi.");
        rules.add("Qisqa savolga qisqa javob bering — hammasini bir javobda tushuntirmang. Har bir "
                + "javobingiz suhbatning davomi bo'lsin, uni boshidan boshlash emas.");
        if (russian) {
            rules.add("Taqiqlangan yozma-rasmiy so'zlar: \"данный\", \"осуществлять\", "
                    + "\"уважаемый клиент\", \"собеседник\", \"мой собеседник\". O'rniga jonli "
                    + "og'zaki shaklni tanlang.");
        } else {
            rules.add("Taqiqlangan yozma-rasmiy so'zlar: \"ushbu\", \"mazkur\", \"amalga oshirish\", "
                    + "\"hurmatli mijoz\", \"hurmatli fuqaro\", \"suhbatdosh\", \"suhbatdoshim\", \"mijoz\".");
            rules.add("Og'zaki shaklni tanlang: \"to'lovni amalga oshirasiz\" emas — \"to'laysiz\"; "
                    + "\"qarzdorligingiz mavjud\" emas — \"qarzingiz bor\"; \"ma'lumot beraman\" "
                    + "emas — \"aytaman\"; \"to'lanishi kerak bo'lgan summa\" emas — \"qarz\".");
            rules.add("Murojaat: faqat ismga \"aka\"/\"opa\" qo'shing (\"Murodjon aka\", \"Shahnoza opa\") — "
                    + "familiya yoki to'liq ism-sharifni hech qachon aytmang.");
            rules.add("QAT'IYAN TAQIQLANADI: \"Siz [Ism]misiz?\", \"Siz [Ism] bo'lasizmi?\". Shaxsni "
                    + "aniqlashda faqat: \"Men [Ism] aka bilan gaplashayapmanmi?\" yoki \"[Ism] aka, sizmisiz?\".");
            rules.add("O'zbek adabiy tilida yozing; o' va g' harflarini doim apostrof bilan "
                    + "(so'm, to'lov, bo'yicha, qo'yaymanmi). So'zni bo'lib yubormang va harfini "
                    + "tushirib qoldirmang — buzuq yozilgan so'z ovozda ham buzuq eshitiladi.");
        }
        rules.add("FAKTLARni qat'iy ayting — ular tekshirilgan, siz ularga shubha qilmaysiz. "
                + (russian ? "\"кажется\", \"вроде\", \"похоже\"" : "\"ekan\", \"shekilli\", \"bo'lsa kerak\", \"chiqibdi\"")
                + " kabi noaniqlik qo'shimchalarini ishlatmang va mijozdan qarzini tasdiqlashini "
                + "so'ramang: qarz bor-yo'qligi muhokama mavzusi emas, siz uni xabar qilyapsiz.");
        rules.add("Summa va sanalarni FAKTLARdagidek raqam bilan yozing (\"1500000 so'm\", \"1-iyul\") — "
                + "so'z bilan yozmang, ovozga aylantirilganda o'zi to'g'ri o'qiladi.");
        rules.add("Joriy yildagi sanada yilni aytmang — \"3-sentabr\" yetarli. Mijoz \"ertaga\" yoki "
                + "\"dushanba\" desa, tasdiqlaganda ham o'sha tabiiy shaklni saqlang "
                + "(\"ertaga, 3-sentabrda\"); to'liq yyyy-MM-dd sana faqat tool parametriga yoziladi.");
        rules.add("Mijoz xavotir yoki norozilik bildirsa — ko'pi bilan ikki so'z bilan tan oling "
                + (russian ? "(\"Понимаю.\")" : "(\"Tushunaman.\")")
                + " va darhol ishga qayting. Hamdardlikni cho'zmang: uzun kechirim so'rash yoki "
                + "his-tuyg'u haqidagi alohida gap mijozni tinchlantirmaydi, faqat qo'ng'iroqni "
                + "uzaytiradi.");
        rules.add("Suhbatni yakunlaydigan gapni (\"xayr\", \"salomat bo'ling\", \"kuningiz xayrli o'tsin\") "
                + "FAQAT endCall tool'i orqali ayting. Natijani yozib bo'lganingizdan keyin mijoz "
                + "\"rahmat\" desa — javob bermang, boshqa tool chaqirmang: endCall bilan xayrlashing. "
                + "Bir suhbatda ikki marta xayrlashish robotdek eshitiladi.");
        rules.add("O'zingizni va kompaniyani bir marta tanishtirasiz — keyingi javoblarda kompaniya "
                + "nomini qayta aytmang.");
        rules.add("Mijoz viloyat shevalarida (Surxondaryo, Xorazm, Samarqand, Farg'ona va boshqalar) yoki "
                + "o'zbek-rus aralash tilda gapirsa: STT matnidagi g'alati yoki shevaga xos so'zlarni kontekstdan to'g'ri tushuning, "
                + "unga e'tiroz bildirmang yoki to'g'irlamang. Asosiy maqsad va ssenariy bo'yicha tabiiy va muloyim davom eting. "
                + "Agar gap mutlaqo tushunarsiz bo'lsa, xushmuomalalik bilan qisqa aniqlashtiring.");
        rules.add("Javobingiz ovozga aylantiriladi: ro'yxat, sarlavha, qavs yoki maxsus belgi ishlatmang.");
        return rules;
    }

    /**
     * Contrast pairs, because a banned phrase is easier to avoid next to the sentence that
     * should have replaced it. Every "wrong" line here was said on a real call.
     */
    private static String examples(boolean russian) {
        if (russian) {
            return """
                    MISOL (✗ noto'g'ri → ✓ to'g'ri):
                    ✗ "Когда ожидается осуществление данного платежа?" → ✓ "Когда сможете оплатить?"
                    ✗ "Вы Мурод?" → ✓ "Я говорю с Мурод-ака?"
                    """;
        }
        return """
                MISOL (✗ noto'g'ri → ✓ to'g'ri):
                ✗ "Ushbu to'lov qachon amalga oshirilishi kutilmoqda?" → ✓ "Qachon to'lay olasiz?"
                ✗ "Siz Murodjonmisiz?" → ✓ "Men Murodjon aka bilan gaplashayapmanmi?"
                ✗ "Sizning A56 shartnomangiz bo'yicha 1500000 so'm qarzingiz bor ekan, to'lov muddati 1-iyulda o'tib ketgan."
                  → ✓ "Murodjon aka, 1500000 so'm qarzingiz bor. To'lov muddati 1-iyulda o'tgan."
                ✗ "Sizda qarzdorlik bor ekan, to'g'rimi?" → ✓ "1500000 so'm qarzingiz bor. Nima uchun to'lanmayapti?"
                ✗ (bo'sh, tasdiqlovchi savol) "1500000 so'm qarzingiz bor, muddati 1-iyulda o'tgan — bu haqda xabaringiz bormidi?"
                  → ✓ "1500000 so'm qarzingiz bor, muddati 1-iyulda o'tgan. Nima uchun to'lanmayapti?"
                ✗ "Murodjon aka, eshitib turibsizmi, to'lovni bu hafta oxirigacha qila olasizmi?"
                  → ✓ "Qachon to'lay olasiz?"
                ✗ (o'tgan javobda "qachon to'laysiz?" so'ralgan) "Bu to'lovni qachon to'lab bera olasiz?"
                  → ✓ "Tushunarli. Sabab nimada — vaqtinchalik qiyinchilikmi?"
                """;
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
                .append("mijoz jimlikni eshitadi. (DIQQAT: reply parametriga inglizcha kod yoki placeholder yozish qat'iyan man etiladi).]");
        String asked = lastQuestion(s.lastAgentText());
        if (asked != null) {
            // The history already holds this, and the model still asked "qachon to'lay
            // olasiz?" twice in a row on a real call — once as REASON_INQUIRY, again as
            // PAYMENT_DATE. Repeating it here puts it next to the generation point.
            sb.append("\n[TIZIM: O'tgan javobingizda shuni so'ragan edingiz: \"").append(asked)
                    .append("\" Uni boshqa so'z bilan qayta so'ramang. Mijoz javob bergan bo'lsa — ")
                    .append("javobini qabul qilib keyingi savolga o'ting; javob bermagan bo'lsa — ")
                    .append("shu bosqichning boshqa jihatini so'rang.]");
        }
        if (s.isInterrupted()) {
            sb.append("\n[TIZIM: Mijoz siz gapirayotganda sizni bo'ldi. Siz shu yergacha aytgan edingiz: \"")
                    .append(s.lastAgentText() == null ? "" : s.lastAgentText())
                    .append("\". Mijozning gapiga moslashing; butun gapni qaytadan boshlamang.]");
        }
        if (s.isLowConfidenceInput()) {
            // The recognizer itself said it was unsure. Acting on a misheard date or sum is
            // how a promise gets recorded for a day the caller never named — a person in
            // this position repeats it back before writing anything down.
            sb.append("\n[TIZIM: Mijozning oxirgi gapi shovqin ichida noaniq eshitildi. ")
                    .append("Unga ishonib ish qilma: eshitganingni qisqa takrorlab tasdiqlat ")
                    .append("(masalan \"To'g'ri eshitdimmi — ...?\"). Tasdiqlanmaguncha sana yoki ")
                    .append("summani tool'ga yozma.]");
        }
        if (s.lastCustomerSentiment() != null && s.lastCustomerSentiment() != CustomerSentiment.NEUTRAL) {
            String directive = sentimentDetector.empathyDirective(s.lastCustomerSentiment(), s.language());
            if (directive != null && !directive.isBlank()) {
                sb.append(directive);
            }
        }
        if (s.disposition() != null) {
            // The outcome tools (recordPaymentPromise, recordRefusalReason,
            // scheduleCallback) record a result without ending the call, and on a real
            // call the model then kept the conversation open with nothing left to ask:
            // it recorded the promise, said "Kelishdik, 2-oktabr deb belgilab qo'ydim",
            // and sat there until the caller hung up on it.
            sb.append("\n[TIZIM: Qo'ng'iroq natijasi allaqachon yozib olindi (")
                    .append(s.disposition())
                    .append("). Boshqa savol bermang va kelishilgan narsani qayta muhokama ")
                    .append("qilmang: kelishuvni bitta qisqa gap bilan tasdiqlab, DARHOL ")
                    .append("endCall tool'i bilan xayrlashing.]");
        }
        int stageAttempts = s.stageAttempts(s.state());
        if (stageAttempts >= 2) {
            sb.append("\n[TIZIM OGOHLANTIRISHI: Siz ushbu '").append(s.state())
                    .append("' bosqichida allaqachon ").append(stageAttempts)
                    .append(" marta takrorladingiz. Mijozdan bir xil savolni QAYTA SO'RAMANG! ")
                    .append("Mijozning e'tirozi yoki savolini qisqa tan olib, zudlik bilan keyingi bosqichga o'ting ")
                    .append("(transitionTo tool'ini chaqiring) yoki agar mijoz rozi bo'lmasa/tushunmasa operatorga uzating (requestHumanTransfer).]");
        }
        return sb.toString();
    }

    /**
     * The question the agent's previous line ended on, or {@code null} when it asked
     * none. Only the last one: a reply carries at most one question by rule, and it is
     * the one the caller was answering.
     */
    static String lastQuestion(String agentText) {
        if (agentText == null) {
            return null;
        }
        int end = agentText.lastIndexOf('?');
        if (end < 0) {
            return null;
        }
        int start = end;
        while (start > 0 && ".!?".indexOf(agentText.charAt(start - 1)) < 0) {
            start--;
        }
        String question = agentText.substring(start, end + 1).trim();
        return question.isEmpty() ? null : question;
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
