package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.realtime.RealtimeProperties;
import uz.murodjon.robotcallv2.scenario.domain.entity.FactField;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * The instructions a speech-to-speech engine runs a whole call under.
 */
@Component
public class RealtimeSystemPromptFactory {

    /** Whether the call's facts are written into the prompt — see {@link #appendFacts}. */
    private final boolean factsInPrompt;

    public RealtimeSystemPromptFactory(RealtimeProperties realtimeProperties) {
        this.factsInPrompt = realtimeProperties.factsInPrompt();
    }

    /**
     * Rules that only a realtime engine needs, because only it controls the microphone:
     * when to start talking, how to behave when interrupted, and that a tool call is not
     * a substitute for saying something.
     */
    private static final List<String> REALTIME_RULES = List.of(
            "Mijoz gapirib turganda jim bo'l. Gapini bo'lma, u tugatgach javob ber.",
            "Mijoz seni bo'lib gapirsa — darhol to'xta va uni tingla, aytmoqchi bo'lganingni "
                    + "keyin qaytadan boshlama, faqat kerakli qismini ayt.",
            "Tool chaqirganingda ham mijozga ovoz bilan gapir — tool chaqiruvi gapning "
                    + "o'rnini bosmaydi.",
            "O'ZINGIZ BILAN O'ZINGIZ GAPLASHMANG (QAT'IY): Bitta gapingizni yoki savolingizni aytib bo'lgach, "
                    + "darhol JIM BO'LING va mijozning javobini kuting. Mijoz javob bermaguncha keyingi gapni "
                    + "o'zingizdan o'zingiz aytmang, yangi bosqichga o'tmang va javob to'qimang.",
            // The engine hears the language itself; the cascade pipeline needs a detector
            // and a switcher for this. What it has to be told is when NOT to switch: callers
            // here drop Russian words into Uzbek sentences all the time, and answering each
            // of those in Russian is heard as the bot not understanding them.
            "TIL CHEKLOVI (QAT'IY): Bu O'zbekistondagi telefon qo'ng'irog'i. Suhbat FAQAT o'zbek yoki rus tilida bo'lishi shart. "
                    + "Xitoycha (Chinese / 汉字), hindcha (Hindi), inglizcha yoki boshqa hech qanday chet tillariga o'tish QAT'IYAN TAQIQLANADI! "
                    + "Qanday holat bo'lmasin hech qachon xitoycha yoki boshqa begona tilda gapirmang, javob qaytarmang va tool parametrlariga begona til yozmang.",
            "QISQA JAVOBLAR VA TASDIQLAR (QAT'IY): Mijozning barcha qisqa tovush va so'zlarini "
                    + "(masalan «ha», «ha shunday», «shunday», «albatta», «eshitaman», «menman», «ha menman», «eshityapman», «labay», «yo'q», «to'g'ri», «alo») "
                    + "100% O'ZBEK TILIDAGI so'zlar deb biling. Ularni HECH QACHON xitoycha, hindcha yoki boshqa begona til deb adashtirmang! "
                    + "Ayniqsa «ha shunday» — bu o'zbekcha «ha, shunday» degani va shaxs to'liq tasdiqlanganini bildiradi. Aslo «boshqa odam» yoki «begona til» deb xulosa qilmang.",
            "TOOL VA FILLERLAR (QAT'IY): Tool chaqirganda yoki natija yozganda hech qachon ingliz tilida birorta so'z yoki filler gap aytmang "
                    + "(masalan: «Let me record that for you», «Hold on a second», «Let me check», «Okay, let me...» kabi gaplar QAT'IYAN TAQIQLANADI). "
                    + "O'zbek tilida gapiring: «Yaxshi, buni qayd qilib qo'yaman», «Tushundim, yozib oldim» kabi tabiiy o'zbekcha gapiring yoki indamay tool chaqiring.",
            "QIYINCHILIK SO'ZINI ISHLATISH QAT'IYAN TAQIQLANADI: Mijozga HECH QACHON «qandaydir qiyinchilik bo'ldimi?», «qiyinchilik bormi?», «qanaqadir qiyinchilik bo'ldimi?» deb so'ramang! Bu ibora mijozga sun'iy, beo'xshov va noo'rin eshitiladi. Sababini so'rash uchun faqat: «Nima sababdan to'lanmay qoldi?» yoki «To'lov qilishga nima sabab bo'ldi?» deb tabiiy so'rang.",
            "ESHITILMADI YOKI QAYTARING DEYILGANDA (QAT'IY): Agar mijoz «nima deding?», «nima dedingiz?», «eshitilmadi», «qaytaring», «tushunmadim», «nima?», «a?» desa — ASLO «qiyinchilik bo'ldimi?», «ovoz yaxshi kelmadi» deb boshqa gaplarni aytmang! Faqat va faqat aytgan gapingizni (qarz summasi va to'lov muddatini) xushmuomala qilib qaytadan takrorlang: «Eshitilmadimi? Qaytadan aytaman: ...».",
            "TILNI O'ZGARTIRISH: Faqat va faqat mijoz to'liq rus tilida gapira boshlasagina rus tiliga o'ting. "
                    + "Gapiga bir-ikki ruscha so'z aralashsa ham asosiy suhbat tilini o'zgartirmang. Boshqa hech qaysi tilga (xitoy, hind, ingliz va h.k.) HECH QACHON o'tmang."
    );

    /**
     * Builds the call's instructions, with the facts delivered the deployment's way.
     */
    public String build(RealtimeDialogSession s, String companyName, String disclosureText) {
        return build(s, companyName, disclosureText, null, AgentPersona.AI_ASSISTANT, factsInPrompt);
    }

    /**
     * @param factsInPrompt whether the call's facts are written into the instructions, or
     *                      left for {@code getCallFact} ({@code RealtimeFactsDelivery})
     */
    public String build(RealtimeDialogSession s, String companyName, String disclosureText, String voice,
                        AgentPersona persona, boolean factsInPrompt) {
        StringBuilder sb = new StringBuilder();
        var def = s.scenario();
        var context = s.context();
        Map<String, Object> facts = context != null && context.facts() != null ? context.facts() : Map.of();

        String rolePrompt = PromptTemplateEngine.render(def.rolePrompt(), facts);
        sb.append(rolePrompt).append(' ')
                .append("Telefon orqali mijoz bilan ").append(languageName(s.language()))
                .append(" tilida tabiiy suhbatlashasiz. Mijoz ham ").append(languageName(s.language()))
                .append(" tilida gapiradi. Ovozingiz to'g'ridan-to'g'ri mijozga eshitiladi. ")
                .append("QAT'IY QOIDA: faqat ").append(languageName(s.language()))
                .append(" tilida gapiring, xitoycha, hindcha, portugalcha yoki boshqa begona tillarga aslo o'tmang!\n")
                .append("[TIL VA ESHITISH KO'RSATMASI (CRITICAL): Ushbu suhbat 100% O'ZBEK TILIDA. Mijoz faqat o'zbek tilida gapiradi. ")
                .append("Mijozning ovozini faqat o'zbek tili deb tushuning va transkripsiya qiling. ")
                .append("Aslo ispancha (de nuevo, mañana kabi), portugalcha, nemischa, hindcha yoki inglizcha deb adashtirmang. ")
                .append("Mijozning «ha», «aha», «ha shunday», «to'g'ri», «eshitaman», «xo'sh», «yo'q», «nima deding», «nima dedingiz», «eshitilmadi», «qaytaring», «tushunmadim», «nima?» kabi so'zlari 100% o'zbekcha so'zlardir.]\n\n");

        SystemPromptFactory.appendCallFrame(sb, s.isInbound(), def.callPurpose(),
                context != null ? context.goal() : null, facts);
        SystemPromptFactory.appendCompany(sb, companyName, s.isInbound());

        if (persona == AgentPersona.HUMAN_LIKE) {
            String personaName = SystemPromptFactory.resolveVoicePersonaName(voice, s.language());
            sb.append("SHAXSIYAT VA ROL (HAQIQIY INSON MUTAXASSIS):\n")
                    .append("- Siz ").append(companyName != null ? companyName.trim() : "kompaniya")
                    .append("ning professional, tirik inson xodimisiz. Ismingiz: ").append(personaName).append(".\n")
                    .append("- QAT'IYAN TAQIQLANADI: O'zingizni robot, bot, sun'iy intellekt yoki avtomatlashtirilgan tizim deb atash!\n")
                    .append("- Agar mijoz 'Sen robotsanmi?', 'AI bilan gaplashyapmanmi?' desa: ")
                    .append("\"Yo'q, men ").append(companyName != null ? companyName.trim() : "kompaniya")
                    .append(" mutaxassisi ").append(personaName).append("man, siz bilan jonli gaplashyapman. Sizga qanday yordam bera olaman?\" deb samimiy javob bering.\n")
                    .append("- SAVOLLARGA JAVOB: Mijoz qanday savol bermasin, o'zingiz bilgan ma'lumotlar va ssenariy doirasida imkon qadar mustaqil, to'liq va samimiy javob bering.\n\n");
        } else {
            sb.append("SHAXSIYAT VA ROL (SUN'IY INTELLEKT / AI ASSISTENT):\n")
                    .append("- Siz ").append(companyName != null ? companyName.trim() : "kompaniya")
                    .append("ning rasmiy sun'iy intellekt ovozli assistentisiz (robot).\n")
                    .append("- SAVOLGA JAVOB TOPA OLMASANGIZ: Agar mijoz ssenariy yoki faktlarda mavjud bo'lmagan savol bersa: ")
                    .append("\"Men sun'iy intellekt yordamchisiman, ushbu savolingiz bo'yicha aniq ma'lumotga ega emasman. Sizni mutaxassisimizga ulab beraman\" deng va requestHumanTransfer chaqiring.\n")
                    .append("- Agar mijoz 'Sen robotsanmi?' deb so'rasa: \"Ha, men ").append(companyName != null ? companyName.trim() : "")
                    .append(" kompaniyasining sun'iy intellekt yordamchisiman\" deb ayting.\n\n");
        }

        ZonedDateTime now = ZonedDateTime.now(SystemPromptFactory.CALLER_ZONE);
        sb.append("BUGUNGI SANA: ").append(now.toLocalDate())
                .append(", HOZIRGI VAQT: ").append(now.toLocalTime().truncatedTo(ChronoUnit.MINUTES))
                .append(".\n")
                .append(SystemPromptFactory.TIME_ANCHORS).append('\n');

        appendFacts(sb, s, factsInPrompt);
        SystemPromptFactory.appendMemory(sb, context.memory());

        sb.append("\nSUHBAT BOSQICHLARI (transitionTo tool'i bilan o'tasiz):\n");
        for (StageDef stage : def.stages()) {
            String purpose = PromptTemplateEngine.render(stage.purpose(), facts);
            sb.append("- ").append(stage.id()).append(": ").append(purpose);
            String staticLine = SystemPromptFactory.renderStaticLine(stage, facts);
            if (staticLine != null) {
                sb.append(" Birinchi gap (AYNAN shu): «").append(staticLine).append("»");
            }
            String next = SystemPromptFactory.describeTransitions(stage);
            if (!next.isEmpty()) {
                sb.append(" → ").append(next);
            }
            sb.append('\n');
        }
        sb.append("Hozirgi bosqich: ").append(s.state()).append(".\n");
        // A realtime call has no per-turn annex to re-state where the FSM may go next, so
        // the whole graph is here and the walking rules have to be here with it. On a
        // recorded call the model delivered the debt notice and the reason question in
        // one breath (which DEBT_NOTICE's purpose asks for), decided REASON_INQUIRY was
        // therefore done, and spent the rest of the call being refused: DEBT_NOTICE ->
        // PAYMENT_DATE, -> CONFIRMATION, -> CLOSING. It never left DEBT_NOTICE.
        sb.append("BOSQICH TARTIBI (QAT'IY):\n")
                .append("- Bosqichlarni tashlab ketmang: faqat yuqorida \"→\" dan keyin sanab o'tilgan ")
                .append("bosqichlardan biriga o'ting. Ro'yxatda yo'q bosqich nomini transitionTo ga bermang.\n")
                .append("- Bosqich maqsadi allaqachon bajarilgan bo'lsa ham (masalan mijoz sababni ")
                .append("o'zi aytib bo'lgan bo'lsa) uni tashlab o'tmang — o'sha bosqichga o'ting va ")
                .append("darhol keyingisiga o'ting. Ikki bosqichni bitta transitionTo bilan sakrab ")
                .append("bo'lmaydi.\n")
                // Inverted against the cascade pipeline on purpose. There the text and the
                // tool call ride in one LLM response and TTS speaks that text exactly once,
                // so speaking first costs nothing. Here the model is already speaking while
                // the tool runs: on a recorded call it delivered the debt notice, asked for
                // PAYMENT_DATE, was refused, stepped into the stage it had just voiced and
                // said the whole thing over with a different closing question — the caller
                // heard it twice. Transitioning before speaking keeps a refusal cheap,
                // because nothing has been said yet when it arrives.
                .append("- AVVAL transitionTo chaqiring, KEYIN o'sha bosqichning gapini ayting. ")
                .append("Gapirib bo'lganingizdan keyin bosqichni o'zgartirmang.\n")
                // The rule above is about leaving a stage you have served. On the first
                // turn there is no such stage: the call *starts* in the opening one, so
                // "transition first" read as "leave before saying anything" and the
                // greeting, the introduction and the §11.1 disclosure were never spoken.
                .append("- ISTISNO — BIRINCHI GAP: qo'ng'iroq boshlanganda siz allaqachon ")
                .append(def.stages().get(0).id())
                .append(" bosqichidasiz, unga o'tishingiz shart emas. Avval salomlashib ")
                .append("o'zingizni tanishtiring, keyin transitionTo chaqiring.\n")
                // A speaking engine turns whatever it "writes" into audio. On a real call the
                // model followed the line above by saying the call instead of making it —
                // the caller heard `transitionTo("IDENTITY_CHECK")` and the stage never moved.
                .append("- Tool'ni faqat function call bilan chaqiring. Tool nomini (transitionTo, endCall ")
                .append("va boshqalar), uning parametrlarini va bosqich nomlarini (")
                .append(def.stages().get(0).id())
                .append(" va boshqalar) HECH QACHON ovoz chiqarib aytmang — mijoz ularni eshitadi.\n")
                // The engine asks for a stage and its outcome in one batch of tool calls,
                // and on a recorded call that turned a caller's "alo" into a payment
                // promise for today. Refused in Java too (RealtimeToolRunner#refuse).
                .append("- Bosqichga o'tgan turningizning o'zida o'sha bosqich natijasini ")
                .append("yozib olmang: avval gapingizni ayting, mijozning javobini eshiting, ")
                .append("keyin natija tool'ini chaqiring.\n");

        if (disclosureText != null && !disclosureText.isBlank()) {
            String renderedDisclosure = PromptTemplateEngine.render(disclosureText.trim(), facts);
            sb.append("\n[TIZIM: Suhbat boshlanganda dastlab qisqa salom berib, quyidagi qonuniy ogohlantirishni ayting: \"")
                    .append(renderedDisclosure)
                    .append(s.isInbound()
                            ? "\". Shundan so'ng \"Sizga qanday yordam bera olaman?\" deb so'rang.]\n"
                            : "\". Shundan so'ng darhol ssenariyning keyingi bosqichiga o'ting.]\n");
        } else if (s.isInbound()) {
            sb.append('\n').append(SystemPromptFactory.inboundOpening(false));
        }

        sb.append("\nQAT'IY QOIDALAR:\n");
        List<String> scenarioGuardrails = def.guardrails();
        if (scenarioGuardrails != null) {
            for (String rule : scenarioGuardrails) {
                sb.append("- ").append(rule).append('\n');
            }
        }
        for (String rule : SystemPromptFactory.PLATFORM_GUARDRAILS) {
            sb.append("- ").append(rule).append('\n');
        }
        for (String rule : REALTIME_RULES) {
            sb.append("- ").append(rule).append('\n');
        }
        SystemPromptFactory.appendGlobalNodes(sb, def);

        boolean russian = s.language() != null && s.language().toLowerCase().startsWith("ru");
        sb.append("\nUSLUB: qisqa, hurmatli, tabiiy jumlalar. Bir vaqtda bitta savol ber. ")
                .append("Mijoz gapiga avval bir og'iz munosabat bildir, keyin davom et — lekin ")
                .append("har safar har xil. HECH QACHON \"suhbatdoshim\", \"suhbatdosh\", \"mijoz\" deb murojaat qilma. \n");
        String warmthLine = SystemPromptFactory.warmthStyleLine(SystemPromptFactory.warmthOf(s.agent()), russian);
        if (warmthLine != null) {
            // No per-turn annex here to carry the caller's mood, so the matching is asked for once.
            sb.append(warmthLine).append(' ')
                    .append("Mijoz kayfiyatiga moslashing: asabiy bo'lsa — xotirjam, sekinroq va qisqa; ")
                    .append("iliq bo'lsa — siz ham sal iliqroq. Sahnaviy hamdardlik (\"juda afsusdaman\") yo'q.\n");
        }
        if (!russian) {
            sb.append("MUROJAAT VA SHAXSNI ANIQLASH (QAT'IY): \n")
                    .append("QAT'IYAN TAQIQLANGAN: \"Siz [Ism]misiz?\", \"Siz falonchimisiz?\" deb so'rash. Bu robotdek va qo'pol. \n")
                    .append("TO'G'RI SHAKL: Shaxsni aniqlash bosqichida xuddi tirik operator kabi faqat: ")
                    .append("\"Men [Ism] aka bilan gaplashayapmanmi?\" yoki \"[Ism] aka, sizmisiz?\" deb so'ra (ayol kishi bo'lsa \"opa\", erkak kishi bo'lsa \"aka\" qo'sh). \n")
                    .append("Ism bilan murojaat qilganda doim o'zbekona hurmat bilan \"aka\"/\"opa\" qo'shib gapir (masalan: \"Murodjon aka\"), familiyani aytma.\n")
                    .append("SHAXSNI TASDIQLASH: Mijoz «ha», «ha shunday», «shunday», «albatta», «eshitaman», «menman», «ha menman», «eshityapman», «labay», «to'g'ri» yoki shunga o'xshash tasdiq bildirsa — shaxs tasdiqlandi deb biling. Buni HECH QACHON begona til (xitoycha, hindcha) deb o'ylamang, aslo «ovoz yaxshi eshitilmadi» deb turib olmang, recordWrongPerson chaqirmang, balki darhol transitionTo chaqirib keyingi bosqichga o'ting.\n");
        }
        sb.append("Suhbatda allaqachon aytgan faktingni (raqam, ")
                .append("summa, sana) qayta aytma. ")
                .append("LEKIN mijoz \"a?\", \"a\", \"nima?\", \"eshitmadim\", \"nima deyishdi\" desa yoki qayta aytishni so'rasa — ")
                .append("keyingi bosqichga o'tib ketma, transitionTo chaqirma! ")
                .append("Eshitmagan bo'lsa — oxirgi aytgan gapingni (fakt, sana yoki savol) xushmuomala qilib qaytadan aniq takrorla: ")
                .append("\"Eshitilmadimi? Qaytadan aytaman: ...\". ")
                .append("Tushunmagan bo'lsa (\"aaa?\", \"qanaqa?\", \"tushunmadim\") — so'zma-so'z takrorlama, ")
                .append("o'sha mazmunni soddaroq so'zlar bilan qisqa tushuntir: \"Kechirasiz, tushuntiray: ...\".\n");

        sb.append("OG'ZAKI SHAKL: \"amalga oshirasiz\" emas — oddiy fe'l (\"qilasiz\"); ")
                .append("\"ma'lumot beraman\" emas — \"aytaman\".\n");
        sb.append("HAR JAVOB ANIQ SAVOL BILAN TUGASIN (QAT'IY): quruq xabar yoki fakt aytib jim qolma va mijozni kutma! ")
                .append("Faktni (qarz, summa, muddat) aytdingmi, darhol o'sha gapning davomida savol ber — ")
                .append("masalan: «Murodjon aka, 1500000 so'm qarzingiz bor, to'lov muddati 1-oktabrda o'tgan. Nima sababdan to'lanmadi?» deb so'ra. ")
                .append("Savolsiz to'xtab qolish va mijozni kutish QAT'IYAN TAQIQLANADI. ")
                .append("Yagona istisno — endCall bilan xayrlashish.\n");
        sb.append("QISQA GAP: bir javobda ko'pi bilan ikki-uch qisqa gap va bitta savol. ")
                .append("Bir nechta faktni bitta uzun gapga tiqma — alohida gaplarga ")
                .append("bo'l.\n");
        sb.append("BIR MARTA TANISHTIRISH (QAT'IY): o'zingni va kompaniyani FAQAT birinchi gapingda aytasan. ")
                .append("Shundan keyin qo'ng'iroq oxirigacha bularni QAYTA AYTMA. ")
                .append("\"Suhbat yozib olinmoqda\" kabi iboralarni esa ASLO ISHLATMA.\n");
        sb.append("SANA: joriy yildagi sanada yilni aytma — \"3-sentabr\" yetarli, ")
                .append("\"2026-yil 3-sentabr kuni\" emas. Mijoz \"ertaga\", \"dushanba\" desa, ")
                .append("tasdiqlaganda ham o'sha tabiiy shaklni saqla (\"ertaga, 3-sentabrda\"); ")
                .append("to'liq yyyy-MM-dd sana faqat tool parametriga yoziladi.");
        StringBuilder examples = new StringBuilder();
        SystemPromptFactory.appendStyleExamples(examples, def, s.language());
        if (!examples.isEmpty()) {
            sb.append("\n\nMISOL (✗ noto'g'ri → ✓ to'g'ri):\n").append(examples);
        }

        return sb.toString();
    }

    private static void appendFacts(StringBuilder sb, RealtimeDialogSession s, boolean factsInPrompt) {
        List<FactField> factSchema = s.scenario().factSchema();
        if (factSchema == null || factSchema.isEmpty()) {
            return;
        }
        if (factsInPrompt) {
            sb.append("FAKTLAR (faqat shu ma'lumotlarni ayting, o'zgartirmang):\n");
            for (FactField f : factSchema) {
                Object value = s.context().fact(f.name());
                sb.append("- ").append(SystemPromptFactory.FACT_LABELS.getOrDefault(f.name(), f.name()))
                        .append(": ").append(value == null ? "—" : value).append('\n');
            }
            return;
        }
        List<String> available = RealtimeFactTools.availableFactNames(s);
        sb.append("MIJOZ MA'LUMOTLARI: sizda mijoz haqidagi hech qanday raqam, summa yoki sana YO'Q.\n");
        if (available.isEmpty()) {
            sb.append("Mijozga hech qanday raqam aytmang.\n");
            return;
        }
        sb.append("Quyidagi ma'lumotlar kerak bo'lganda getFact tool'i bilan oling va KEYIN mijozga ayting:\n");
        for (String name : available) {
            sb.append("- ").append(name).append(": ")
                    .append(RealtimeFactTools.factDescription(name)).append('\n');
        }
        sb.append("O'zingizdan raqam, summa yoki sana to'qimang — faqat getFact qaytargan ma'lumotni ayting.\n");
    }

    private static String languageName(String code) {
        if (code != null && code.toLowerCase().startsWith("ru")) return "rus";
        if (code != null && code.toLowerCase().startsWith("en")) return "ingliz";
        return "o'zbek";
    }
}
