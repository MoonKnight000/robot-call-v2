package uz.murodjon.robotcallv2.agent.summary;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.agent.metrics.VoiceMetrics;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Scores finished calls against a rubric, so a change to the prompt, the model or the
 * endpointing is visible as a quality number rather than as a complaint three weeks later.
 *
 * <p>Separate from {@link SummaryService} on purpose, even though both read the same
 * transcript. That one exists to fill the CRM in — its output is written to
 * {@code call_result} and shown to people, so it must keep working exactly as it does. This
 * one exists to judge <em>us</em>: it asks different questions ("did the agent state a
 * figure the facts do not support?", "did it answer in the wrong language?"), it is
 * sampled rather than run on everything, and its answer goes to the metrics and the log
 * instead of the customer's record. Mixing the two would make every future change to the
 * QA rubric a change to what the CRM receives.
 *
 * <p>Off by default: it is a second LLM call per call, and it buys nothing on a deployment
 * nobody is watching. Turn it on around a change, read
 * {@code voice.qa.score} and {@code voice.qa.flags}, and turn the sample rate down again.
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
                            @Value("${voice-agent.quality.enabled:false}") boolean enabled,
                            @Value("${voice-agent.quality.sample-rate:1.0}") double sampleRate,
                            @Value("${voice-agent.quality.model:gemini-3.8-flash}") String model,
                            @Value("${voice-agent.quality.max-tokens:512}") int maxTokens) {
        this.chatModelProvider = chatModelProvider;
        this.metrics = metrics;
        this.enabled = enabled;
        this.sampleRate = sampleRate;
        this.model = model;
        this.maxTokens = maxTokens;
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
            log.warn("Call quality judge has no LLM ChatModel (set GEMINI_API_KEY); scoring disabled");
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
        if (chatClient == null || transcript == null || transcript.isBlank() || !sampled()) {
            return null;
        }
        try {
            Map<String, Object> raw = chatClient.prompt()
                    .options(GoogleGenAiChatOptions.builder()
                            .model(model)
                            .maxOutputTokens(maxTokens)
                            .thinkingLevel(GoogleGenAiThinkingLevel.LOW)
                            .build())
                    .system(RUBRIC + rules(scenario))
                    .user("QO'NG'IROQ YAKUNI: " + disposition + "\n\nTRANSKRIPT:\n" + transcript)
                    .call()
                    .entity(new MapOutputConverter());
            QualityVerdict verdict = toVerdict(raw);
            if (verdict != null) {
                publish(verdict);
            }
            return verdict;
        } catch (Exception e) {
            log.warn("Call quality scoring failed: {}", e.getMessage());
            return null;
        }
    }

    /** Whether this call is one of the ones scored. */
    private boolean sampled() {
        return sampleRate >= 1.0 || (sampleRate > 0 && ThreadLocalRandom.current().nextDouble() < sampleRate);
    }

    /** The scenario's own rules, so the judge marks against the same ones the agent was given. */
    private static String rules(ScenarioDefinition scenario) {
        if (scenario == null || scenario.guardrails() == null || scenario.guardrails().isEmpty()) {
            return "";
        }
        return "\nSSENARIY QOIDALARI:\n- " + String.join("\n- ", scenario.guardrails()) + '\n';
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
