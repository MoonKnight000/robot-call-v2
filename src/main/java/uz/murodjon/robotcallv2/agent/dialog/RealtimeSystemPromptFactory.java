package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.agent.realtime.RealtimeProperties;
import uz.murodjon.robotcallv2.scenario.domain.entity.FactField;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;

import java.time.LocalDate;
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
            "Jimlikni uzoq cho'zma: mijoz bir necha soniya jim tursa, qisqa savol bilan "
                    + "suhbatni davom ettir."
    );

    /**
     * Builds the call's instructions.
     */
    public String build(RealtimeDialogSession s, String companyName, String disclosureText) {
        StringBuilder sb = new StringBuilder();
        var def = s.scenario();
        var context = s.context();
        Map<String, Object> facts = context != null && context.facts() != null ? context.facts() : Map.of();

        String rolePrompt = PromptTemplateEngine.render(def.rolePrompt(), facts);
        sb.append(rolePrompt).append(' ')
                .append("Telefon orqali mijoz bilan ").append(languageName(s.language()))
                .append(" tilida tabiiy suhbatlashasiz. Ovozingiz to'g'ridan-to'g'ri mijozga ")
                .append("eshitiladi.\n\n");

        if (companyName != null && !companyName.isBlank()) {
            sb.append("KOMPANIYA: siz \"").append(companyName.trim())
                    .append("\" kompaniyasi nomidan qo'ng'iroq qilyapsiz. O'zingizni tanishtirganda ")
                    .append("faqat shu nomni ayting — yuqoridagi matnda boshqa nom bo'lsa ham.\n\n");
        }

        LocalDate today = LocalDate.now();
        sb.append("BUGUNGI SANA: ").append(today).append(".\n\n");

        appendFacts(sb, s);
        if (context.goal() != null && !context.goal().isBlank()) {
            sb.append("- Kampaniya maqsadi: ").append(context.goal()).append('\n');
        }

        sb.append("\nSUHBAT BOSQICHLARI (transitionTo tool'i bilan o'tasiz):\n");
        for (StageDef stage : def.stages()) {
            String purpose = PromptTemplateEngine.render(stage.purpose(), facts);
            sb.append("- ").append(stage.id()).append(": ").append(purpose);
            if (stage.allowedTransitions() != null && !stage.allowedTransitions().isEmpty()) {
                sb.append(" → ").append(String.join(", ", stage.allowedTransitions()));
            }
            sb.append('\n');
        }
        sb.append("Hozirgi bosqich: ").append(s.state()).append(".\n");

        if (disclosureText != null && !disclosureText.isBlank()) {
            String renderedDisclosure = PromptTemplateEngine.render(disclosureText.trim(), facts);
            sb.append("\n[TIZIM: Suhbat boshlanganda dastlab qisqa salom berib, quyidagi qonuniy ogohlantirishni ayting: \"")
                    .append(renderedDisclosure)
                    .append("\". Shundan so'ng darhol ssenariyning keyingi bosqichiga o'ting.]\n");
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

        boolean russian = s.language() != null && s.language().toLowerCase().startsWith("ru");
        sb.append("\nUSLUB: qisqa, hurmatli, tabiiy jumlalar. Bir vaqtda bitta savol ber. ")
                .append("Mijoz gapiga avval bir og'iz munosabat bildir, keyin davom et — lekin ")
                .append("har safar har xil. HECH QACHON \"suhbatdoshim\", \"suhbatdosh\", \"mijoz\" deb murojaat qilma. \n");
        if (!russian) {
            sb.append("MUROJAAT VA SHAXSNI ANIQLASH (QAT'IY): \n")
                    .append("QAT'IYAN TAQIQLANGAN: \"Siz [Ism]misiz?\", \"Siz falonchimisiz?\" deb so'rash. Bu robotdek va qo'pol. \n")
                    .append("TO'G'RI SHAKL: Shaxsni aniqlash bosqichida xuddi tirik operator kabi faqat: ")
                    .append("\"Men [Ism] aka bilan gaplashayapmanmi?\" yoki \"[Ism] aka, sizmisiz?\" deb so'ra (ayol kishi bo'lsa \"opa\", erkak kishi bo'lsa \"aka\" qo'sh). \n")
                    .append("Ism bilan murojaat qilganda doim o'zbekona hurmat bilan \"aka\"/\"opa\" qo'shib gapir (masalan: \"Murodjon aka\"), familiyani aytma.\n");
        }
        sb.append("Suhbatda allaqachon aytgan faktingni (shartnoma ")
                .append("raqami, summa, muddat) qayta aytma; mijoz eshitmagan bo'lsa faqat ")
                .append("so'ralgan qismini takrorla.\n");

        sb.append("OG'ZAKI SHAKL: \"to'lovni amalga oshirasiz\" emas — \"to'laysiz\"; ")
                .append("\"qarzdorligingiz mavjud\" emas — \"qarzingiz bor ekan\"; ")
                .append("\"ma'lumot beraman\" emas — \"aytaman\"; ")
                .append("\"to'lanishi kerak bo'lgan summa\" emas — \"qarz\".\n");
        sb.append("QISQA GAP: bir javobda ko'pi bilan ikki-uch qisqa gap va bitta savol. ")
                .append("Shartnoma, summa va muddatni bitta uzun gapga tiqma — alohida gaplarga ")
                .append("bo'l. O'zingni va kompaniyani bir marta tanishtirasan, keyingi ")
                .append("javoblarda kompaniya nomini qayta aytma.\n");
        sb.append("SANA: joriy yildagi sanada yilni aytma — \"3-sentabr\" yetarli, ")
                .append("\"2026-yil 3-sentabr kuni\" emas. Mijoz \"ertaga\", \"dushanba\" desa, ")
                .append("tasdiqlaganda ham o'sha tabiiy shaklni saqla (\"ertaga, 3-sentabrda\"); ")
                .append("to'liq yyyy-MM-dd sana faqat tool parametriga yoziladi.");

        return sb.toString();
    }

    private void appendFacts(StringBuilder sb, RealtimeDialogSession s) {
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
