package uz.murodjon.uysotvoice.agent.dialog;

import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.agent.realtime.RealtimeProperties;
import uz.murodjon.uysotvoice.scenario.dto.FactField;
import uz.murodjon.uysotvoice.scenario.dto.StageDef;

import java.time.LocalDate;
import java.util.List;

/**
 * The instructions a speech-to-speech engine runs a whole call under.
 *
 * <p>Separate from {@link SystemPromptFactory} rather than a variant of it, because half
 * of what that class writes only makes sense for the cascade pipeline and would actively
 * mislead here: it tells the model to put its spoken line in a tool's {@code reply}
 * argument (a realtime engine speaks by speaking), to write sums as digits so the TTS
 * normalizer can read them out (there is no TTS step), to spell Uzbek apostrophes
 * correctly so the synthesizer does not mangle them (nothing is synthesized from text),
 * and it re-sends the current stage every turn (there are no turns to re-send it on).
 *
 * <p>What does carry over is shared, not copied: the platform guardrails and the fact
 * labels come from {@link SystemPromptFactory}'s own constants, so §4.4's rules exist in
 * one place.
 *
 * <p>The whole FSM is described up front, since this prompt is sent once at connect and
 * never revised — the engine has to be able to find its own way from any stage to the
 * next without being told again.
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
     *
     * @param s           the call, for its scenario, facts and language
     * @param companyName whose name the agent introduces itself with, or {@code null}
     * @param disclosureSpoken whether the §11.1 notice has already been played from code,
     *                         so the engine is told not to open with it a second time
     */
    public String build(RealtimeDialogSession s, String companyName, boolean disclosureSpoken) {
        StringBuilder sb = new StringBuilder();
        var def = s.scenario();
        var context = s.context();

        sb.append(def.rolePrompt()).append(' ')
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

        // The whole flow at once: nothing re-sends it mid-call, so a stage the engine
        // cannot see here is a stage it will never reach.
        sb.append("\nSUHBAT BOSQICHLARI (transitionTo tool'i bilan o'tasiz):\n");
        for (StageDef stage : def.stages()) {
            sb.append("- ").append(stage.id()).append(": ").append(stage.purpose());
            if (stage.allowedTransitions() != null && !stage.allowedTransitions().isEmpty()) {
                sb.append(" → ").append(String.join(", ", stage.allowedTransitions()));
            }
            sb.append('\n');
        }
        sb.append("Hozirgi bosqich: ").append(s.state()).append(".\n");

        if (disclosureSpoken) {
            sb.append("\n[TIZIM: Salomlashuv va \"avtomatik xizmat, suhbat yozib olinmoqda\" ")
                    .append("ogohlantirishi allaqachon aytildi — mijoz ularni eshitib bo'ldi. ")
                    .append("Salom bilan ham, o'zingni tanishtirish bilan ham boshlama; ")
                    .append("to'g'ridan-to'g'ri ish bilan boshla.]\n");
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

    /**
     * The facts section — which, by default, contains no facts.
     *
     * <p>Writing the debt amount here is what the cascade pipeline does, and it can
     * afford to: it reads the model's sentence before anything is synthesized and
     * withholds it if the figure is wrong. A realtime engine has already spoken by the
     * time there is text to read, so the prompt is the last place where a wrong figure
     * can still be prevented instead of merely noticed — and the way to prevent it is not
     * to put the figure there. The engine is told which facts <em>exist</em> and made to
     * fetch each one at the moment it says it ({@link RealtimeFactTools}).
     *
     * <p>{@code voice-agent.realtime.facts-in-prompt=true} restores the cascade's shape
     * for deployments that would rather have the latency than the guarantee.
     */
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
