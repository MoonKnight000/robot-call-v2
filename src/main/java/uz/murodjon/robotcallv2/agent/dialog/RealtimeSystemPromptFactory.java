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
            sb.append("\n[TIZIM: Suhbat boshlanganda dastlab salom berib, quyidagi qonuniy ogohlantirishni ayting: \"")
                    .append(renderedDisclosure)
                    .append("\". Shundan so'ng darhol suhbat maqsadiga o'ting.]\n");
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

        sb.append("\nUSLUB: qisqa, hurmatli, tabiiy jumlalar. Bir vaqtda bitta savol ber. ")
                .append("Mijoz gapiga avval bir og'iz munosabat bildir, keyin davom et — lekin ")
                .append("har safar har xil. Suhbatda allaqachon aytgan faktingni (shartnoma ")
                .append("raqami, summa, muddat) qayta aytma; mijoz eshitmagan bo'lsa faqat ")
                .append("so'ralgan qismini takrorla.");

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
            sb.append("Bu qo'ng'iroqda umuman ma'lumot yo'q — raqam yoki sana aytmang.\n");
            return;
        }
        sb.append("Quyidagilarni getCallFact tool'i orqali olishingiz mumkin: ")
                .append(String.join(", ", available)).append(".\n")
                .append("Har qanday summa, sana, shartnoma raqami yoki ismni aytishdan OLDIN ")
                .append("getCallFact ni chaqiring va qaytgan qiymatni aynan o'sha holda ayting. ")
                .append("Xotirangizdan yoki taxmin bilan raqam aytish qat'iyan taqiqlanadi — ")
                .append("noto'g'ri summa aytilsa qo'ng'iroq operatorga o'tkaziladi.\n");
    }

    private static String languageName(String bcp47) {
        if (bcp47 == null) {
            return "o'zbek";
        }
        return bcp47.toLowerCase().startsWith("ru") ? "rus" : "o'zbek";
    }
}
