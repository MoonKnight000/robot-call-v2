package uz.murodjon.robotcallv2.shared.dialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pre-tool speech phrases spoken intermediate lines before executing tools,
 * preventing awkward dead silence during long or external tool operations.
 */
public final class PreToolPhrases {

    private PreToolPhrases() {
    }

    private static final Map<String, String> UZ_TOOL_PHRASES = Map.of(
            "sendSmsPaymentLink", "Hozir to'lov havolasini SMS orqali yuborishni rasmiylashtiryapman...",
            "recordPaymentPromise", "Hozir to'lov ma'lumotlarini tizimda qayd qilyapman...",
            "requestPaymentExtension", "Hozir to'lov muddatini uzaytirish so'rovingizni tekshiryapman...",
            "scheduleCallback", "Hozir qayta qo'ng'iroq vaqtini belgilayapman...",
            "requestHumanTransfer", "Bir daqiqa, sizni operatorga ulayapman...",
            "knowledgeBaseQuery", "Hozir bu savol bo'yicha ma'lumotni aniqlashtiryapman..."
    );

    private static final Map<String, String> RU_TOOL_PHRASES = Map.of(
            "sendSmsPaymentLink", "Сейчас оформляю отправку ссылки на оплату по СМС...",
            "recordPaymentPromise", "Сейчас фиксирую данные по оплате в системе...",
            "requestPaymentExtension", "Сейчас проверяю возможность продления срока оплаты...",
            "scheduleCallback", "Сейчас фиксирую время для повторного звонка...",
            "requestHumanTransfer", "Минуточку, соединяю вас с оператором...",
            "knowledgeBaseQuery", "Сейчас уточняю информацию по вашему вопросу..."
    );

    private static final Map<String, String> EN_TOOL_PHRASES = Map.of(
            "sendSmsPaymentLink", "Setting up the payment link via SMS now...",
            "recordPaymentPromise", "Recording the payment details in the system...",
            "requestPaymentExtension", "Checking options for payment extension now...",
            "scheduleCallback", "Scheduling the callback time now...",
            "requestHumanTransfer", "One moment, connecting you to an operator...",
            "knowledgeBaseQuery", "Looking up the information for your question..."
    );

    /**
     * The pre-tool phrase mapped for the tool in this language, or {@code null} when it has none.
     *
     * <p>No generic fallback: CRM lookups answer in a few hundred milliseconds, and "let me check"
     * before every one of them was heard several times a call. A wait that really runs long is
     * covered by the thinking filler (SpeechOutput#scheduleFiller).
     */
    public static String forTool(String toolName, String language) {
        if (toolName == null || toolName.isBlank()) {
            return null;
        }
        if (russian(language)) {
            return RU_TOOL_PHRASES.get(toolName);
        }
        if (english(language)) {
            return EN_TOOL_PHRASES.get(toolName);
        }
        return UZ_TOOL_PHRASES.get(toolName);
    }

    /**
     * Generic filler phrase.
     */
    public static String generic(String language) {
        if (english(language)) {
            return "One moment, please...";
        }
        return russian(language)
                ? "Секунду, пожалуйста..."
                : "Bir soniya, iltimos...";
    }

    /**
     * Returns all phrases in this category for warm-up cache synthesis.
     */
    public static List<String> allPhrases(String language) {
        List<String> list = new ArrayList<>();
        list.add(generic(language));
        if (russian(language)) {
            list.addAll(RU_TOOL_PHRASES.values());
        } else if (english(language)) {
            list.addAll(EN_TOOL_PHRASES.values());
        } else {
            list.addAll(UZ_TOOL_PHRASES.values());
        }
        return List.copyOf(list);
    }

    private static boolean russian(String language) {
        return language != null && language.startsWith("ru");
    }

    private static boolean english(String language) {
        return language != null && language.startsWith("en");
    }
}
