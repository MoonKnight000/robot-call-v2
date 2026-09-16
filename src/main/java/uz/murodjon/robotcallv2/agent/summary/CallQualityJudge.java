package uz.murodjon.robotcallv2.agent.summary;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.agent.metrics.VoiceMetrics;
import uz.murodjon.robotcallv2.scenario.domain.entity.OutcomeField;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.ToolDef;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Scores finished calls against a rubric, so a change to the prompt, the model or the
 * endpointing is visible as a quality number rather than as a complaint three weeks later.
 *
 * <p>Separate from {@link SummaryService} on purpose, even though both read the same
 * transcript. That one exists to fill the CRM in — its output is written to
 * {@code call_result} and shown to people, so it must keep working exactly as it does. This
 * one exists to judge <em>us</em>: it asks different questions ("did the agent state a
 * figure the facts do not support?", "did it answer in the wrong language?"), it is
 * sampled rather than run on everything, and its answer goes to the metrics, the log and
 * {@code call_quality} (UNIVERSAL-OPERATOR-PLAN D.1) — never to the customer's record. Mixing
 * the two would make every future change to the QA rubric a change to what the CRM receives.
 *
 * <p>On by default since D.1, every call sampled: the review inbox and the daily insight job
 * read what it finds. It is a second LLM call per call — once its flags are trusted, lower
 * {@code QUALITY_SAMPLE_RATE} (the plan's target is 0.2).
 *
 * <p>Best-effort throughout — a failed or unparseable verdict is logged and dropped. QA
 * that can break a call is worse than no QA.
 */
@Service
public class CallQualityJudge {

    private static final Logger log = LoggerFactory.getLogger(CallQualityJudge.class);

    private static final String RUBRIC = """
            Siz avtomatik qo'ng'iroq agentini baholovchi qat'iy QA auditorisiz.
            Sizga AGENT va CLIENT gaplaridan iborat transkript, ssenariy qoidalari va
            qo'ng'iroq qanday yakunlangani (disposition) beriladi.
            Faqat transkriptdagi dalilga tayaning — taxmin qilmang.
            Quyidagi maydonlar bo'yicha JSON qaytaring:
            - score: agent qo'ng'iroqni qanday olib borgani, 0 dan 100 gacha butun son.
            - guardrailViolation: agent faktlarda yo'q summa, sana yoki majburiyatni aytdimi,
              yoki ssenariy qoidasini buzdimi (true/false). Eng jiddiy belgi.
            - languageMismatch: agent mijoz gapirgan tildan boshqa tilda javob berdimi (true/false).
            - talkedOverClient: agent mijozning gapini bo'ldimi yoki u gapini tugatmasdan
              javob berdimi (true/false).
            - outcomeCorrect: qo'ng'iroq yozilgan yakun (disposition) transkriptdagi haqiqiy
              holatga mos keladimi (true/false).
            - goalReached: qo'ng'iroq quyida berilgan MAQSADga erishdimi (true/false).
            - unansweredQuestion: mijoz savol berdi-yu, agent unga javob bera olmadi,
              "bilmayman" dedi yoki javobni aylanib o'tdi (true/false).
            - toolMisuse: agent tool bilan bajariladigan ishni (buyurtma, bron, ma'lumotni
              tekshirish) tool'siz bajargandek gapirdi, natijani o'zi to'qidi yoki kerakli
              ma'lumotni so'ramasdan ishni yakunladi (true/false). Transkriptda dalil bo'lmasa false.
            - note: eng jiddiy kamchilik haqida bitta jumla (o'zbekcha). Kamchilik bo'lmasa null.
            """;

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final VoiceMetrics metrics;
    private final boolean enabled;
    private final double sampleRate;
    private final String model;
    private final int maxTokens;

    private volatile ChatClient chatClient;

    public CallQualityJudge(ObjectProvider<ChatModel> chatModelProvider,
                            VoiceMetrics metrics,
                            QualityProperties qualityProperties) {
        this.chatModelProvider = chatModelProvider;
        this.metrics = metrics;
        this.enabled = qualityProperties.enabled();
        this.sampleRate = qualityProperties.sampleRate();
        this.model = qualityProperties.model();
        this.maxTokens = qualityProperties.maxTokens();
    }

    @PostConstruct
    public void init() {
        if (!enabled) {
            return;
        }
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel != null) {
            chatClient = ChatClient.create(chatModel);
            log.info("Call quality judge ready (model={}, sample rate={})", model, sampleRate);
        } else {
            log.warn("Call quality judge has no LLM ChatModel (set GEMINI_API_KEY or OPENAI_API_KEY); scoring disabled");
        }
    }

    /**
     * Score one finished call, if this one is in the sample.
     *
     * @param transcript  the whole call, AGENT and CLIENT lines
     * @param scenario    what the agent was supposed to be doing — the rules it is judged against
     * @param disposition what the call was filed as, which is itself one of the things judged
     * @return the verdict, or {@code null} when scoring is off, not sampled, or failed
     */
    public QualityVerdict judge(String transcript, ScenarioDefinition scenario, Disposition disposition) {
        if (!sampled()) {
            return null;
        }
        QualityVerdict verdict = score(transcript, scenario, disposition);
        if (verdict != null) {
            publish(verdict);
        }
        return verdict;
    }

    /**
     * Scores {@code transcript} every time and leaves the production QA metrics alone: for
     * an agent evaluation (NEXT-PLAN A.2), whose simulated calls are not calls.
     *
     * @return the verdict, or {@code null} when scoring is off or failed
     */
    public QualityVerdict evaluate(String transcript, ScenarioDefinition scenario, Disposition disposition) {
        return score(transcript, scenario, disposition);
    }

    private QualityVerdict score(String transcript, ScenarioDefinition scenario, Disposition disposition) {
        if (chatClient == null || transcript == null || transcript.isBlank()) {
            return null;
        }
        try {
            ChatModel cm = chatModelProvider.getIfAvailable();
            boolean isOpenAi = (cm instanceof OpenAiChatModel)
                    || (model != null && (model.startsWith("llama") || model.startsWith("mixtral")
                    || model.startsWith("gpt-") || model.startsWith("qwen")));
            ChatOptions options;
            if (isOpenAi) {
                options = OpenAiChatOptions.builder()
                        .model(model)
                        .maxTokens(maxTokens)
                        .build();
            } else {
                options = GoogleGenAiChatOptions.builder()
                        .model(model)
                        .maxOutputTokens(maxTokens)
                        .thinkingLevel(GoogleGenAiThinkingLevel.LOW)
                        .build();
            }
            Map<String, Object> raw = chatClient.prompt()
                    .options(options)
                    .system(RUBRIC + rules(scenario))
                    .user("QO'NG'IROQ YAKUNI: " + disposition + "\n\nTRANSKRIPT:\n" + transcript)
                    .call()
                    .entity(new MapOutputConverter());
            return toVerdict(raw);
        } catch (Exception e) {
            log.warn("Call quality scoring failed: {}", e.getMessage());
            return null;
        }
    }

    /** Whether this call is one of the ones scored. */
    private boolean sampled() {
        return sampleRate >= 1.0 || (sampleRate > 0 && ThreadLocalRandom.current().nextDouble() < sampleRate);
    }

    /**
     * What the agent is marked against: the scenario's own rules — the same ones it was
     * given — its goal, and the tools it had, since a tool it never reached for is only a
     * miss if it had one.
     */
    private static String rules(ScenarioDefinition scenario) {
        if (scenario == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (scenario.guardrails() != null && !scenario.guardrails().isEmpty()) {
            sb.append("\nSSENARIY QOIDALARI:\n- ").append(String.join("\n- ", scenario.guardrails())).append('\n');
        }
        sb.append("\nMAQSAD: ").append(goalOf(scenario)).append('\n');
        if (scenario.tools() != null && !scenario.tools().isEmpty()) {
            sb.append("AGENTDAGI TOOL'LAR: ")
                    .append(scenario.tools().stream().map(ToolDef::name).collect(Collectors.joining(", ")))
                    .append('\n');
        }
        return sb.toString();
    }

    /**
     * The goal field when the scenario names one (UNIVERSAL-OPERATOR-PLAN A.4), otherwise
     * the role it gave the agent — the only statement of purpose such a scenario has.
     */
    private static String goalOf(ScenarioDefinition scenario) {
        if (scenario.outcomeSchema() != null) {
            for (OutcomeField field : scenario.outcomeSchema()) {
                if (field.goal()) {
                    return field.name() + (field.description() != null ? " — " + field.description() : "");
                }
            }
        }
        return scenario.rolePrompt();
    }

    private void publish(QualityVerdict verdict) {
        metrics.recordQualityScore(verdict.score());
        if (verdict.guardrailViolation()) {
            metrics.qualityFlag("guardrail");
        }
        if (verdict.languageMismatch()) {
            metrics.qualityFlag("language");
        }
        if (verdict.talkedOverClient()) {
            metrics.qualityFlag("talked_over");
        }
        if (!verdict.outcomeCorrect()) {
            metrics.qualityFlag("outcome");
        }
        if (verdict.unansweredQuestion()) {
            metrics.qualityFlag("unanswered");
        }
        if (verdict.toolMisuse()) {
            metrics.qualityFlag("tool_misuse");
        }
        // A guardrail violation is the one finding that is never acceptable, so it is
        // logged where an alert can pick it up rather than as one more scored call.
        if (verdict.guardrailViolation()) {
            log.error("QA: guardrail violation judged in a finished call — {}", verdict.note());
        } else if (verdict.needsReview()) {
            log.warn("QA: score {}, needs review — {}", verdict.score(), verdict.note());
        } else {
            log.info("QA: score {}", verdict.score());
        }
    }

    private static QualityVerdict toVerdict(Map<String, Object> raw) {
        if (raw == null) {
            return null;
        }
        return new QualityVerdict(
                intOf(raw.get("score")),
                Boolean.TRUE.equals(raw.get("guardrailViolation")),
                Boolean.TRUE.equals(raw.get("languageMismatch")),
                Boolean.TRUE.equals(raw.get("talkedOverClient")),
                // Absent means the judge said nothing about it; treating that as "wrong"
                // would flag every call whose JSON came back short.
                !Boolean.FALSE.equals(raw.get("outcomeCorrect")),
                Boolean.TRUE.equals(raw.get("goalReached")),
                Boolean.TRUE.equals(raw.get("unansweredQuestion")),
                Boolean.TRUE.equals(raw.get("toolMisuse")),
                raw.get("note") == null ? null : raw.get("note").toString());
    }

    private static int intOf(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
