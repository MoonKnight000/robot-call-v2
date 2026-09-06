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
     * Returns an appropriate pre-tool phrase for the tool and language,
     * or a generic polite filler if no tool-specific phrase is mapped.
     */
    public static String forTool(String toolName, String language) {
        if (toolName == null || toolName.isBlank()) {
            return generic(language);
        }
        if (russian(language)) {
            String phrase = RU_TOOL_PHRASES.get(toolName);
            return phrase != null ? phrase : "Секунду, выполняю операцию...";
        }
        if (english(language)) {
            String phrase = EN_TOOL_PHRASES.get(toolName);
            return phrase != null ? phrase : "One moment, processing your request...";
        }
        String phrase = UZ_TOOL_PHRASES.get(toolName);
        return phrase != null ? phrase : "Hozir bir soniya tekshirib ko'raman...";
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
            list.add("Секунду, выполняю операцию...");
        } else if (english(language)) {
            list.addAll(EN_TOOL_PHRASES.values());
            list.add("One moment, processing your request...");
        } else {
            list.addAll(UZ_TOOL_PHRASES.values());
            list.add("Hozir bir soniya tekshirib ko'raman...");
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
