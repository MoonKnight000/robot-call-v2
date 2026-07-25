package uz.murodjon.uysotvoice.agent.summary;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uz.murodjon.uysotvoice.agent.dialog.CallSummary;

/**
 * Produces the structured {@link CallSummary} from a finished call's transcript
 * (PROJECT.md §4.3). A single LLM call with a stronger model (Sonnet — quality
 * matters, latency does not) and Spring AI's {@code .entity()} structured output.
 * Non-fatal: returns {@code null} if the LLM is unavailable or parsing fails.
 */
@Service
public class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

    private static final String SYSTEM_PROMPT = """
            Siz qarz undirish qo'ng'irog'i transkriptini tahlil qiluvchi yordamchisiz.
            Sizga AGENT va CLIENT gaplaridan iborat transkript beriladi.
            Undan quyidagi maydonlarni ajratib, faqat so'ralgan struktura bo'yicha qaytaring:
            - summary: 2-3 jumlada CRM uchun qisqacha xulosa (o'zbekcha).
            - reasonCode: to'lanmaslik sababi (ma'lum bo'lmasa null).
            - promisedDate: va'da qilingan to'lov sanasi yyyy-MM-dd (bo'lmasa null).
            - promisedAmount: va'da qilingan summa (bo'lmasa null).
            - sentiment: mijoz kayfiyati.
            - needsFollowUp: qayta qo'ng'iroq kerakmi.
            - followUpNote: qayta qo'ng'iroq uchun izoh (bo'lmasa null).
            Faktlarni o'ylab topmang — faqat transkriptdagi ma'lumotga tayaning.
            """;

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final boolean enabled;
    private final String model;

    private volatile ChatClient chatClient;

    public SummaryService(ObjectProvider<ChatModel> chatModelProvider,
                          @Value("${voice-agent.summary.enabled:true}") boolean enabled,
                          @Value("${voice-agent.summary.model:gemini-2.5-pro}") String model) {
        this.chatModelProvider = chatModelProvider;
        this.enabled = enabled;
        this.model = model;
    }

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("Call summary disabled (voice-agent.summary.enabled=false)");
            return;
        }
        ChatModel cm = chatModelProvider.getIfAvailable();
        if (cm != null) {
            chatClient = ChatClient.create(cm);
            log.info("Summary service ready (model={})", model);
        } else {
            log.warn("Summary service has no LLM ChatModel (set GEMINI_API_KEY + GEMINI_API_BASE_URL); summaries disabled");
        }
    }

    /** Summarize {@code transcript}; returns {@code null} on empty input or failure. */
    public CallSummary summarize(String transcript) {
        if (chatClient == null || transcript == null || transcript.isBlank()) {
            return null;
        }
        try {
            return chatClient.prompt()
                    .options(OpenAiChatOptions.builder().model(model).build())
                    .system(SYSTEM_PROMPT)
                    .user(transcript)
                    .call()
                    .entity(CallSummary.class);
        } catch (Exception e) {
            log.warn("Summary generation failed: {}", e.getMessage());
            return null;
        }
    }
}
