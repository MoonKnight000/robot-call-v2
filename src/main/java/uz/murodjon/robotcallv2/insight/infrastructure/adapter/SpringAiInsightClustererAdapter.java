package uz.murodjon.robotcallv2.insight.infrastructure.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.insight.application.port.output.InsightClustererPort;
import uz.murodjon.robotcallv2.insight.domain.entity.AgentCallSignals;
import uz.murodjon.robotcallv2.insight.domain.entity.InsightProposal;
import uz.murodjon.robotcallv2.insight.domain.enums.InsightType;
import uz.murodjon.robotcallv2.insight.infrastructure.config.InsightProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Asks a model which of a day's signals are the same problem, and what to do about each
 * (UNIVERSAL-OPERATOR-PLAN D.3).
 *
 * <p>The model is given counts and one-line notes, never transcripts, and is told in so many
 * words that it may not invent a cause the numbers do not show. What comes back is checked
 * here rather than trusted: an unknown type, a missing title or a count larger than the
 * window had calls in it drops the proposal, and everything that survives is trimmed to what
 * the column will hold. A proposal is only ever a suggestion to a person, but a suggestion
 * that cites a number nobody measured wastes that person's morning.
 *
 * <p>No model configured — no insights, and a daily job that finishes quietly. The loop is an
 * extra, not something the platform stops without.
 */
@Component
public class SpringAiInsightClustererAdapter implements InsightClustererPort {

    private static final Logger log = LoggerFactory.getLogger(SpringAiInsightClustererAdapter.class);

    /** What the column holds; a longer title is the model writing a paragraph in the wrong field. */
    private static final int MAX_TITLE_CHARS = 200;
    private static final int MAX_TEXT_CHARS = 2000;

    private static final String INSTRUCTIONS = """
            Siz ovozli AI agentlarning sifatini tahlil qiluvchi mutaxassissiz. Sizga bitta
            agentning bir sutkalik qo'ng'iroqlaridan yig'ilgan BELGILAR beriladi: sonlar,
            sifat sudyasining qisqa izohlari va supervayzerning "agent xato qildi" sabablari.
            Transkript berilmaydi.

            Vazifa: shu belgilardan nechta HAQIQIY muammo ko'rinayotganini aniqlang va har biri
            uchun bitta aniq taklif yozing. Bir xil muammoni ikki marta yozmang.

            QAT'IY QOIDALAR:
            - Faqat berilgan sonlar va izohlarga tayaning. Sabab to'qimang, "ehtimol" deb
              taxmin qilmang. Belgilar kam bo'lsa — kam taklif yozing yoki umuman yozmang.
            - Har bir taklifda callCount — shu muammo ko'ringan qo'ng'iroqlar soni, berilgan
              sonlardan olinadi.
            - title, detail va suggestion — AGENT TILIDA (pastda ko'rsatilgan), operator
              o'qiydigan tirik gap bilan.

            type quyidagilardan biri:
            - PROMPT_FIX: agent aytmasligi kerak bo'lgan narsani aytgan. suggestion — agent
              qoidalari ro'yxatiga O'ZGARTIRISHSIZ qo'shsa bo'ladigan BITTA buyruq gap
              (masalan: "Chegirma yoki bepul yetkazib berish va'da qilmang."). Faqat bitta gap,
              sarlavhasiz, raqamsiz, qo'shtirnoqsiz.
            - KB_GAP: mijozlar so'ragan savolga agentda javob yo'q. suggestion — bilimlar
              bazasiga qanday mavzu qo'shilishi kerakligi (javob matnini o'zingiz yozmang,
              uni kompaniya biladi).
            - TOOL_FIX: tashqi tool xato qaytaryapti yoki noto'g'ri chaqirilyapti. suggestion —
              tool yoki uning parametri tavsifida nima aniqlashtirilishi kerak.
            - NEW_TEST_CASE: shu muammo qayta takrorlanmasligi uchun test holati kerak.
              suggestion — mijoz rolini o'ynaydigan modelga beriladigan persona matni: u kim,
              nima istaydi, qanday bosim o'tkazadi (2-4 gap).
            - PRONUNCIATION: so'z noto'g'ri eshitilyapti yoki noto'g'ri o'qilyapti.
              suggestion — qaysi so'z va qanday o'qilishi kerakligi.
            - HANDOFF_PATTERN: operatorga o'tkazish kech yoki keraksiz sodir bo'lyapti.
              suggestion — nimani o'zgartirish kerakligi.

            Javob FAQAT JSON bo'lsin, shu ko'rinishda:
            {"insights": [{"type": "...", "title": "...", "detail": "...", "suggestion": "...", "callCount": 0}]}
            Muammo ko'rinmasa: {"insights": []}
            """;

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final InsightProperties insightProperties;

    public SpringAiInsightClustererAdapter(ObjectProvider<ChatModel> chatModelProvider,
                                           InsightProperties insightProperties) {
        this.chatModelProvider = chatModelProvider;
        this.insightProperties = insightProperties;
    }

    @Override
    public List<InsightProposal> cluster(String agentName, String language, AgentCallSignals signals) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            log.warn("No LLM ChatModel (set GEMINI_API_KEY or OPENAI_API_KEY); no insights will be proposed");
            return List.of();
        }
        try {
            Map<String, Object> answer = ChatClient.create(chatModel).prompt()
                    .options(options())
                    .system(INSTRUCTIONS)
                    .user(describe(agentName, language, signals))
                    .call()
                    .entity(new MapOutputConverter());
            return proposals(answer, signals);
        } catch (Exception e) {
            log.warn("Insight clustering for agent '{}' failed: {}", agentName, e.getMessage());
            return List.of();
        }
    }

    /** The same model choice the quality judge makes, for the same reason: either vendor may be the one configured. */
    private ChatOptions options() {
        String model = insightProperties.model();
        boolean openAi = chatModelProvider.getIfAvailable() instanceof OpenAiChatModel
                || model.startsWith("gpt-") || model.startsWith("llama") || model.startsWith("qwen");
        return openAi
                ? OpenAiChatOptions.builder().model(model).maxTokens(insightProperties.maxTokens()).build()
                : GoogleGenAiChatOptions.builder().model(model)
                        .maxOutputTokens(insightProperties.maxTokens()).build();
    }

    /** The window as the model reads it: what happened, how often, and what was said about it. */
    private static String describe(String agentName, String language, AgentCallSignals signals) {
        StringBuilder sb = new StringBuilder();
        sb.append("AGENT: ").append(agentName).append('\n');
        sb.append("AGENT TILI: ").append(language != null ? language : "uz-UZ").append('\n');
        sb.append("QO'NG'IROQLAR (oxirgi sutka): ").append(signals.calls()).append('\n');
        if (signals.avgScore() != null) {
            sb.append("O'RTACHA SIFAT BALLI: ").append(signals.avgScore()).append(" / 100\n");
        }
        sb.append("BELGILAR (qo'ng'iroqlar soni bilan):\n")
                .append("- qoidabuzarlik (aytmasligi kerak bo'lgan narsani aytdi): ")
                .append(signals.guardrailViolations()).append('\n')
                .append("- javobsiz savol: ").append(signals.unansweredQuestions()).append('\n')
                .append("- tool'ni noto'g'ri ishlatdi: ").append(signals.toolMisuses()).append('\n')
                .append("- noto'g'ri til: ").append(signals.languageMismatches()).append('\n')
                .append("- mijozning gapini bo'ldi: ").append(signals.talkedOverClient()).append('\n')
                .append("- tool xatosi bo'lgan qo'ng'iroqlar: ").append(signals.toolErrorCalls()).append('\n')
                .append("- sutka ichida qayta qo'ng'iroq qilgan mijozlar: ").append(signals.repeatCalls()).append('\n')
                .append("- operatorga o'tkazilgan: ").append(signals.transfers()).append('\n')
                .append("- mijoz \"nima?\", \"qaytaring\" degan holatlar (jami): ").append(signals.repairs()).append('\n')
                .append("- jimlik uchun qayta so'rash (jami): ").append(signals.noInputPrompts()).append('\n')
                .append("- nutqni tanib olish ishonchi past gaplar (jami): ").append(signals.lowConfidence()).append('\n');
        appendLines(sb, "SIFAT SUDYASI IZOHLARI", signals.judgeNotes());
        appendLines(sb, "SUPERVAYZER \"AGENT XATO QILDI\" SABABLARI", signals.reviewerNotes());
        return sb.toString();
    }

    private static void appendLines(StringBuilder sb, String heading, List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        sb.append(heading).append(":\n");
        for (String line : lines) {
            sb.append("- ").append(line).append('\n');
        }
    }

    @SuppressWarnings("unchecked")
    private List<InsightProposal> proposals(Map<String, Object> answer, AgentCallSignals signals) {
        if (answer == null || !(answer.get("insights") instanceof List<?> raw)) {
            return List.of();
        }
        List<InsightProposal> proposals = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof Map<?, ?> fields) {
                InsightProposal proposal = proposal((Map<String, Object>) fields, signals);
                if (proposal != null) {
                    proposals.add(proposal);
                }
            }
        }
        return proposals;
    }

    private static InsightProposal proposal(Map<String, Object> fields, AgentCallSignals signals) {
        InsightType type = typeOf(text(fields.get("type")));
        String title = trim(text(fields.get("title")), MAX_TITLE_CHARS);
        if (type == null || title.isEmpty()) {
            return null;
        }
        int callCount = Math.min(number(fields.get("callCount")), signals.calls());
        return new InsightProposal(type, title, trim(text(fields.get("detail")), MAX_TEXT_CHARS),
                trim(text(fields.get("suggestion")), MAX_TEXT_CHARS), callCount, signals.sampleCalls());
    }

    private static InsightType typeOf(String value) {
        try {
            return InsightType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static int number(Object value) {
        return value instanceof Number n ? Math.max(n.intValue(), 0) : 0;
    }

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
