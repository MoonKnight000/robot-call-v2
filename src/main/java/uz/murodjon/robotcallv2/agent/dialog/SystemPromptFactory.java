package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgePassage;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.memory.domain.entity.RememberedCall;
import uz.murodjon.robotcallv2.scenario.domain.entity.FactField;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
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
                    + "uzr so'rab xayrlash.",
            // On a real call the caller's answer came back as "aha o'zgartir" — grammatical
            // Uzbek, unrelated to the question — and the agent replied "Pul bo'lmagani uchun
            // to'lolmayapman deng", inventing the one thing the whole call is meant to find
            // out. A reason the caller never gave is a fact the recording says the company
            // put in their mouth, so it is banned at platform level, not per scenario.
            "Mijoz AYTMAGAN gapni unga nisbat bermang. Sabab, niyat, rozilik yoki va'dani faqat "
                    + "mijozning o'zi ochiq aytgan bo'lsa takrorlang. \"...deng\", \"...demoqchisiz\", "
                    + "\"demak siz...\", \"tushundim, siz...\" shakllari bilan mijoz aytmagan sababni "
                    + "to'qish — eng og'ir xato. Mijozning javobi savolingizga javob bo'lmasa, bo'shliqni "
                    + "o'zingizdan to'ldirmang: savolni qayta bering."
    );

    /**
     * A retrieved passage is about a chunk long; the cap guards against a source whose
     * extractor produced one enormous run of text, not a normal trim. Sanitizing matters
     * more than the length: the text comes out of a file an operator uploaded, so anything
     * in it that looks like a system marker is neutralised before it reaches the model.
     */
    private static final int MAX_PASSAGE_CHARS = 1200;
    private static final int MAX_SOURCE_NAME_CHARS = 80;

    /** Uzbek label for a well-known fact name; falls back to the raw name otherwise. */
    /** Remembered calls are dated in the client's local day, the way the agent would say it. */
    private static final ZoneId MEMORY_ZONE = ZoneId.of("Asia/Tashkent");

    /**
     * Cross-call memory: what earlier conversations with this phone established, plus the
     * operator's notes. Newest call first, so the model leans on the latest state. Shared
     * with {@link RealtimeSystemPromptFactory} so both pipelines remember the same way.
     */
    static void appendMemory(StringBuilder sb, ClientMemory memory) {
        if (memory == null || memory.isEmpty()) {
            return;
        }
        sb.append("\nMULTI-CALL MEMORY (Mijoz bilan avvalgi suhbatlar xotirasi va operator eslatmalari — ")
                .append("bulardan tabiiy foydalaning, lekin \"bazamizda yozilgan\" deb aytmang):\n");
        if (memory.preferredName() != null) {
            sb.append("- Mijozga qulay murojaat: ").append(memory.preferredName()).append('\n');
        }
        if (memory.operatorNotes() != null) {
            sb.append("- Operator eslatmasi: ").append(memory.operatorNotes()).append('\n');
        }
        for (RememberedCall call : memory.recentCalls()) {
            sb.append("- Avvalgi suhbat");
            if (call.at() != null) {
                sb.append(" (").append(LocalDate.ofInstant(call.at(), MEMORY_ZONE)).append(')');
            }
            if (call.disposition() != null) {
                sb.append(", natija: ").append(call.disposition());
            }
            sb.append(": ").append(call.summary()).append('\n');
        }
        memory.facts().forEach((name, value) -> sb.append("- ")
                .append(FACT_LABELS.getOrDefault(name, name)).append(" (avvalgi suhbatdan): ")
                .append(value).append('\n'));
    }

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

        if (s.agentPersona() == AgentPersona.HUMAN_LIKE) {
            String personaName = resolveVoicePersonaName(s.ttsVoice(), s.language());
            sb.append("SHAXSIYAT VA ROL (HAQIQIY INSON MUTAXASSIS):\n")
                    .append("- Siz ").append(s.companyName() != null ? s.companyName().trim() : "kompaniya")
                    .append("ning professional, tirik inson xodimisiz. Ismingiz: ").append(personaName).append(".\n")
                    .append("- QAT'IYAN TAQIQLANADI: O'zingizni robot, bot, sun'iy intellekt yoki avtomatlashtirilgan tizim deb atash!\n")
                    .append("- Agar mijoz 'Sen robotsanmi?', 'AI bilan gaplashyapmanmi?', 'Tirik odammisan?' deb so'rasa: ")
                    .append("\"Yo'q, men ").append(s.companyName() != null ? s.companyName().trim() : "kompaniya")
                    .append(" mutaxassisi ").append(personaName).append("man, siz bilan jonli gaplashyapman. Sizga qanday yordam bera olaman?\" deb samimiy va ishonarli javob bering.\n")
                    .append("- BARCHA SAVOLLARGA JAVOB BERISH: Mijoz qanday savol bermasin, o'zingiz bilgan ma'lumotlar va ssenariy doirasida imkon qadar mustaqil, to'liq va odamdek samimiy javob berishga harakat qiling. Qisqa 'bilmayman' deb to'xtab qolmang.\n\n");
        } else {
            sb.append("SHAXSIYAT VA ROL (SUN'IY INTELLEKT / AI ASSISTENT):\n")
                    .append("- Siz ").append(s.companyName() != null ? s.companyName().trim() : "kompaniya")
                    .append("ning rasmiy sun'iy intellekt ovozli assistentisiz (robot).\n")
                    .append("- SAVOLGA JAVOB TOPA OLMASANGIZ: Agar mijoz ssenariy yoki faktlarda mavjud bo'lmagan, siz bilmaydigan yoki tushunarsiz savol bersa, aslo yolg'on ma'lumot to'qimang. To'g'ridan-to'g'ri o'zingizning sun'iy intellekt ekanligingizni ayting: ")
                    .append("\"Men sun'iy intellekt yordamchisiman, ushbu savolingiz bo'yicha aniq ma'lumotga ega emasman. Sizni mutaxassisimizga ulab beraman\" deng va requestHumanTransfer chaqiring.\n")
                    .append("- Agar mijoz 'Sen robotsanmi?' deb so'rasa: \"Ha, men ").append(s.companyName() != null ? s.companyName().trim() : "")
                    .append(" kompaniyasining sun'iy intellekt yordamchisiman\" deb ochiq ayting.\n\n");
        }

        LocalDate today = LocalDate.now();
        sb.append("BUGUNGI SANA: ").append(today).append(" (").append(weekdayUz(today.getDayOfWeek()))
                .append(").\n");
        sb.append(dateAnchors(today)).append('\n');

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

        appendMemory(sb, c.memory());

        if (s.isDisclosureSpoken()) {
            sb.append("\n[TIZIM: Salomlashuv va \"avtomatik xizmat, suhbat yozib olinmoqda\" ")
                    .append("ogohlantirishi allaqachon aytildi — mijoz ularni eshitib bo'ldi.\n")
                    .append("Shuning uchun QO'NG'IROQNING HECH BIR JOYIDA — na birinchi javobda, ")
                    .append("na undan keyingilarida — salomlashma, o'zingni va kompaniyani qayta ")
                    .append("tanishtirma, suhbat yozib olinayotganini qayta aytma (\"Assalomu alaykum\", ")
                    .append("\"Salom\", \"Men ... kompaniyasidanman\", \"... sun'iy intellekt ")
                    .append("yordamchisiman\", \"Suhbat yozib olinmoqda\" — hech biri). Bularni faqat ")
                    .append("mijozning o'zi so'raganda ayt. Birinchi javobing ")
                    .append("to'g'ridan-to'g'ri ssenariyning navbatdagi bosqichiga o'tib, jonli, tabiiy insondek boshlansin: ")
                    .append("shaxsni tasdiqlashda xuddi tajribali tirik operator kabi \"Men Murodjon aka bilan gaplashayapmanmi?\" yoki \"[Ism] aka, sizmisiz?\" deb so'ra ")
                    .append("(QAT'IYAN TAQIQLANADI: \"siz [Ism]misiz?\", \"suhbatdoshim\", \"suhbatdosh\" yoki \"mijoz\" deb aytish). Agar shaxsni so'rash kerak bo'lmasa, muloyimlik bilan maqsadga o't.]\n");
        } else {
            if (s.agentPersona() == AgentPersona.HUMAN_LIKE) {
                String personaName = resolveVoicePersonaName(s.ttsVoice(), s.language());
                sb.append("\n[TIZIM: Birinchi javobingizda salomlashing, o'zingizni odamdek tanishtiring: \"Salom, mening ismim ")
                        .append(personaName).append(". ")
                        .append(s.companyName() != null ? s.companyName().trim() + " kompaniyasidan qo'ng'iroq qilyapman. " : "")
                        .append("\" hamda DARHOL ssenariy bo'yicha keyingi bosqichga (masalan: shaxsni tasdiqlash uchun \"Men Murodjon aka bilan gaplashayapmanmi?\" yoki asosiy maqsadga) o'ting: ")
                        .append("gapingizni oddiy matn qilib yozing, so'ng transitionTo tool'ini chaqiring. Shunchaki salomlashib to'xtab qolmang.]\n");
            } else {
            sb.append("\n[TIZIM: Birinchi javobingizda qisqa salomlashing, o'zingizni va kompaniyani tanishtiring ")
                    .append("hamda DARHOL ssenariy bo'yicha keyingi bosqichga (masalan: shaxsni tasdiqlash uchun \"Men Murodjon aka bilan gaplashayapmanmi?\" yoki asosiy maqsadga) ")
                    .append("o'ting: gapingizni oddiy matn qilib yozing, so'ng transitionTo tool'ini chaqiring. ")
                    .append("Shunchaki salomlashib to'xtab qolmang. QAT'IYAN TAQIQLANADI: \"Siz [Ism]misiz?\" deb so'rash.]\n");
            }
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
     * The relative dates a caller actually names, worked out in advance.
     *
     * <p>The scenario asks the model to turn "keyingi oyning 20-sanasi" into a
     * {@code yyyy-MM-dd} tool argument, and calendar arithmetic is what it then spends its
     * thinking budget on. On a real call the turn that recorded the payment promise — the
     * one turn the whole call exists for — took 5.2s to its first token, against 1.1s for
     * the turns either side of it. Handing it the answers makes that a lookup.
     *
     * <p>Part of the stable prefix, so it is written once per call and cached with the
     * rest of it (the prefix is already rebuilt per call, which is what keeps it right
     * across midnight).
     */
    private static String dateAnchors(LocalDate today) {
        LocalDate nextMonth = today.plusMonths(1);
        LocalDate nextMonthEnd = nextMonth.withDayOfMonth(nextMonth.lengthOfMonth());
        return "SANALAR (tayyor hisoblangan — o'zingiz hisoblab o'tirmang):\n"
                + "- ertaga: " + today.plusDays(1) + "\n"
                + "- indinga: " + today.plusDays(2) + "\n"
                + "- kelasi hafta shu kun: " + today.plusWeeks(1) + "\n"
                + "- shu oyning oxiri: " + today.withDayOfMonth(today.lengthOfMonth()) + "\n"
                + "- keyingi oy: " + nextMonth.withDayOfMonth(1) + " dan " + nextMonthEnd + " gacha. "
                + "Ya'ni \"keyingi oyning N-sanasi\" = " + nextMonth.withDayOfMonth(1).toString().substring(0, 8)
                + "N (masalan 20-sanasi = " + nextMonth.withDayOfMonth(Math.min(20, nextMonth.lengthOfMonth())) + "). "
                + "N ni mijoz aytadi — o'zingiz tanlamaysiz.\n";
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
        rules.add("Har bir javobingiz ALBATTA savol bilan tugasin, va u FAQAT ishni oldinga "
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
        // A garbled answer used to cost the whole question: on a real call the caller's
        // reply came back as noise, the model said "Gapingizni to'liq tushunolmadim" and
        // then asked the next stage's question instead, so the reason it had just asked
        // for was never given. What pushed it forward was the rule above about not
        // repeating a question, which is why the exception has to sit next to that rule.
        rules.add("Mijozning javobi tushunarsiz chiqsa yoki bergan savolingizga javob bo'lmasa — "
                + "keyingi bosqichga ham, boshqa savolga ham o'tmang: qisqa uzr bilan O'SHA savolni "
                + "soddaroq qilib bir marta qayta bering "
                + (russian ? "(\"Плохо слышно. Почему не оплачено?\")" : "(\"Ovoz yaxshi kelmadi. Nima uchun to'lanmayapti?\")")
                + ". Bu — yuqoridagi \"bergan savolingizni qayta bermang\" qoidasiga istisno. "
                + "Ikkinchi urinishda ham tushunarsiz bo'lsa, uchinchi marta so'ramang: ssenariy "
                + "bo'yicha davom eting. Javob grammatik jihatdan to'g'ri, lekin savolingizga aloqasi "
                + "bo'lmasa ham (masalan "
                + (russian ? "\"ага, поменяй\"" : "\"aha o'zgartir\"")
                + ") — xuddi shunday yo'l tuting: uni savolingizga moslab talqin qilmang va javobni "
                + "o'zingizdan to'qimang.");
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
        // "Aniq sana" had to be spelled out: on a real call the caller answered "keyingi
        // oy", the model read that as an exact date, recorded 2026-10-05 — a day nobody
        // named — and closed the call in the same turn. A promise the caller never made is
        // worse than one extra question.
        rules.add("ANIQ SANA = kun raqami aytilgan ("
                + (russian ? "\"20 октября\", \"завтра\", \"5-го\"" : "\"20-oktabr\", \"ertaga\", \"oyning 5-si\"")
                + "). Faqat oy yoki noaniq muddat aytilsa ("
                + (russian ? "\"в следующем месяце\", \"в октябре\", \"в конце месяца\"" : "\"keyingi oy\", \"oktabrda\", \"oy oxirida\"")
                + ") bu ANIQ SANA EMAS: kun raqamini O'ZINGIZ TANLAMANG va recordPaymentPromise "
                + "chaqirmang — qaysi kun ekanini bir marta so'rang.");
        rules.add("Mijoz aniq to'lov sanasini aytgan zahoti SHU javobning o'zida recordPaymentPromise "
                + "va endCall'ni birga chaqiring. Sanani qayta tasdiqlatuvchi savol bermang "
                + (russian ? "(\"20 октября сможете оплатить 1500000?\")" : "(\"20-oktabrda 1500000 so'm to'lay olasizmi?\")")
                + " — mijoz sanani allaqachon aytdi, uni qayta so'rash bitta ortiqcha savol-javob "
                + "qo'shadi. Yakuniy gap savol emas, XABAR bo'lsin: sana va summani takrorlab, "
                + "to'lovni kutayotganingizni ayting va xayrlashing.");
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
                    ✗ (ответ клиента не разобран) "Я вас не совсем понял. Когда сможете оплатить?"
                      → ✓ "Плохо слышно. Почему не оплачено?"
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
                ✗ (mijoz "20-sanada" dedi, endi tasdiqlatyapti) "Aha. 20-oktabrda 1500000 so'm to'lay olasizmi?"
                  → ✓ "Kelishdik. 20-oktabr kuni 1500000 so'mni kutib qolamiz. Salomat bo'ling!"
                ✗ (mijoz javobi tushunarsiz chiqdi) "Gapingizni to'liq tushunolmadim. Bu to'lovni qachon to'lay olasiz?"
                  → ✓ "Ovoz yaxshi kelmadi. Nima uchun to'lanmayapti?"
                ✗ (mijoz "aha o'zgartir" dedi — bu "nima uchun to'lanmayapti?" savoliga javob emas)
                  "Pul bo'lmagani uchun to'lolmayapman deng. Xo'p, qachon to'lay olasiz?"
                  → ✓ "Ovoz yaxshi kelmadi. Nima uchun to'lanmayapti?"
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
        // The FSM refuses an edge that is not in `allowedNext`, and a model that spoke a
        // later stage's content first then tried to jump there got the refusal mid-turn
        // and started reasoning aloud about which tool to call instead — the caller heard
        // it. What bounds that is the "one of the allowed next stages, never further"
        // half of the rule below, and that half is kept.
        //
        // The other half used to say "transition now, say that stage's line next turn",
        // which contradicts both the scenario rule in the prefix ("move, and say what
        // that stage needs") and the text-before-tool latency rule right underneath. On a
        // recorded call the model resolved the contradiction by speaking each stage's
        // line one turn before transitioning into it, so `s.state()` — and therefore this
        // whole annex — described a stage whose content the caller had already heard:
        // REASON_INQUIRY's annex arrived on the turn the agent was asking for the date,
        // and the reason it was supposed to collect was never collected. Pairing the text
        // and the transitionTo on the same stage in the same turn is what removes the lag.
        sb.append("Bu javobda ikki yo'ldan biri: yo shu bosqichning gapini aytasiz, yo yuqoridagi ")
                .append("ruxsat etilgan bosqichlardan BIRIGA o'tib, o'shaning gapini aytasiz. ")
                .append("O'tayotgan bo'lsangiz — matn va transitionTo bitta javobda va bitta bosqichga ")
                .append("tegishli bo'lsin: avval o'sha bosqichning matnini yozing, so'ng transitionTo ")
                .append("bilan aynan o'sha bosqichga o'ting. Undan narigi bosqichlarning mazmuniga ")
                .append("(savol, taklif, xulosa) sakramang — har bosqich o'z navbatida aytiladi.\n");
        // Text first, tool second — this is a latency rule, not a style one. A line carried
        // in a tool argument arrives in one chunk when the whole reply is finished (this
        // provider does not stream tool arguments), so synthesis cannot start until
        // generation ends: on a recorded call the caller's first audio came 59ms after the
        // first token, i.e. the whole 2.4-6.2s of generation was dead air. Plain text
        // streams, and TurnRunner#consume speaks each sentence as it completes. A model
        // that ignores this and fills "reply" anyway still works — that path is the
        // fallback in TurnRunner#streamTurn, only slower.
        //
        // EVERY tool, not just transitionTo. Naming only transitionTo is what this said
        // before, and on a measured call the model obeyed it exactly: it wrote text on
        // the transitionTo turns and, on the turn it called recordPaymentPromise, emitted
        // the function call alone with no text and no `reply` — "empty LLM reply in
        // REASON_INQUIRY", then a whole extra round trip (996ms) to ask for the sentence
        // the turn should already have had.
        sb.append("Mijozga aytadigan gapingizni AVVAL oddiy matn qilib yozing — u yozilishi bilanoq ")
                .append("ovozga beriladi. HAR QANDAY tool — transitionTo, recordPaymentPromise, ")
                .append("recordRefusalReason, scheduleCallback, endCall va boshqalari — SHU MATNDAN ")
                .append("KEYIN chaqiriladi, va gap uning \"reply\" parametrida QAYTA YOZILMAYDI. ")
                .append("Tool chaqirib, matn yozmaslik — eng qo'pol xato: mijoz jimlikni eshitadi ")
                .append("va javobi bir necha soniyaga kechikadi. Natijani yozayotgan bo'lsangiz ham ")
                .append("(sana, summa, sabab), avval mijozga aytadigan gapni yozing. ")
                .append("(DIQQAT: inglizcha kod yoki placeholder yozish qat'iyan man etiladi).]");
        String asked = lastQuestion(s.lastAgentText());
        if (asked != null) {
            // The history already holds this, and the model still asked "qachon to'lay
            // olasiz?" twice in a row on a real call — once as REASON_INQUIRY, again as
            // PAYMENT_DATE. Repeating it here puts it next to the generation point.
            // "Ask another aspect of this stage" used to be the no-answer branch here, and
            // it contradicted the speech rule that says to re-ask the same question. This
            // note sits closest to the generation point, so its branch is the one that won:
            // handed an answer that was not one, the model moved on — and filled the gap it
            // was moving past with a reason the caller never gave.
            sb.append("\n[TIZIM: O'tgan javobingizda shuni so'ragan edingiz: \"").append(asked)
                    .append("\" Uni boshqa so'z bilan qayta so'ramang. Mijoz javob bergan bo'lsa — ")
                    .append("javobini qabul qilib keyingi savolga o'ting. Javob bermagan bo'lsa yoki ")
                    .append("gapi shu savolga aloqasiz bo'lsa — javobni o'zingizdan to'qimang va uni ")
                    .append("savolga moslab talqin qilmang: o'sha savolni soddaroq qilib bir marta ")
                    .append("qayta bering.]");
        }
        if (s.isInterrupted()) {
            // A barge-in means the caller was talking over the question, so what came back
            // is very often not an answer to it. On the call that prompted this the agent
            // was cut off mid-question, got "aha o'zgartir", and treated it as the reason
            // it had asked for.
            sb.append("\n[TIZIM: Mijoz siz gapirayotganda sizni bo'ldi. Siz shu yergacha aytgan edingiz: \"")
                    .append(s.lastAgentText() == null ? "" : s.lastAgentText())
                    .append("\". Mijozning gapiga moslashing; butun gapni qaytadan boshlamang. ")
                    .append("Mijoz savolingizni to'liq eshitmagan bo'lishi mumkin — uning gapini ")
                    .append("savolingizga javob deb hisoblamang.]");
        }
        appendKnowledge(sb, s);
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
     * The company's own documents, for the one turn they bear on.
     *
     * <p>In the annex rather than the system prefix on purpose: the prefix is cached
     * byte-for-byte by the provider, and passages change every turn. Putting them there
     * would invalidate the cache on every single turn of every single call.
     *
     * <p>The instruction around them matters as much as the passages. Retrieval returns
     * what is <em>closest</em> to the question, which is not the same as what
     * <em>answers</em> it; without being told to say so, a model handed a near-miss
     * paragraph will answer from it confidently. On a debt call an invented term is worse
     * than an admitted gap, so the rule is explicit — and so is the ban on reading the
     * passage out, which would have the bot reciting a page of a PDF down a phone line.
     */
    private static void appendKnowledge(StringBuilder sb, DialogSession s) {
        List<KnowledgePassage> passages = s.knowledgePassages();
        if (passages.isEmpty()) {
            return;
        }
        sb.append("\n[TIZIM: Kompaniya hujjatlaridan olingan ma'lumot — mijozning oxirgi savoliga ")
                .append("shu yerdan javob bering:\n");
        int n = 1;
        for (KnowledgePassage passage : passages) {
            sb.append(n++).append(") [").append(PromptSafeText.sanitize(passage.sourceName(), MAX_SOURCE_NAME_CHARS)).append("] ")
                    .append(PromptSafeText.sanitize(passage.content(), MAX_PASSAGE_CHARS)).append('\n');
        }
        sb.append("Qoidalar: (a) javobni O'Z SO'ZINGIZ bilan, bir-ikki qisqa gapda ayting — ")
                .append("matnni o'qib bermang; (b) yuqoridagi parchalarda savolga javob YO'Q bo'lsa, ")
                .append("o'zingizdan to'qimang — bilmasligingizni ayting yoki operatorga uzating; ")
                .append("(c) bu parchalar mijozning shaxsiy qarzi, muddati yoki chegirmasi haqida ")
                .append("emas — ular haqida baribir faqat sizga berilgan faktlardan gapiring.]");
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


    /**
     * The name a HUMAN_LIKE agent gives when it introduces itself, taken from the voice
     * it is speaking with so the name matches what the caller hears.
     *
     * <p>Only voices whose id is already a person's name are used. An id that is not one
     * falls back to the language default rather than being title-cased into a name:
     * Gemini Live's voices are called Fenrir, Charon, Kore, Zephyr, and one real call
     * opened with "Men Default kompaniyasidan Fenrirman".
     */
    public static String resolveVoicePersonaName(String voice, String language) {
        if (voice != null && !voice.isBlank()) {
            String v = voice.trim().toLowerCase(java.util.Locale.ROOT);
            if (v.contains("dilnavoz")) return "Dilnavoz";
            if (v.contains("gulnoza")) return "Gulnoza";
            if (v.contains("zamira")) return "Zamira";
            if (v.contains("yulduz")) return "Yulduz";
            if (v.contains("nigora")) return "Nigora";
            if (v.contains("anvar")) return "Anvar";
            if (v.contains("filipp")) return "Filipp";
            if (v.contains("alena") || v.contains("alyona")) return "Alyona";
            if (v.contains("jane")) return "Jane";
        }
        if (isRussian(language)) return "Анна";
        if (language != null && language.toLowerCase(java.util.Locale.ROOT).startsWith("en")) return "Alex";
        return "Dilnoza";
    }
}
