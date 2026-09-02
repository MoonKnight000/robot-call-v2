package uz.murodjon.robotcallv2.shared.dialog;

import java.util.ArrayList;
import java.util.List;

/**
 * The lines the agent speaks from code rather than from the model: the §11.1
 * disclosure, the goodbye a guardrail closes on, and the "say that again" fallback
 * used when the LLM returns nothing speakable.
 *
 * <p>They live here, outside the dialog engine, because two components need the exact
 * same strings. The engine speaks them; the TTS warm-up synthesizes them at startup so
 * they are already in the cache when the first call lands.
 */
public final class DialogPhrases {

    private DialogPhrases() {
    }

    /**
     * The §11.1 disclosure: automated system, call is recorded.
     */
    public static String disclosure(String language, String companyName) {
        boolean ru = russian(language);
        boolean en = english(language);
        if (companyName == null || companyName.isBlank()) {
            if (en) return "Hello! This is an automated voice assistant. This call is being recorded.";
            return ru
                    ? "Здравствуйте! Это автоматический голосовой сервис. Разговор записывается."
                    : "Assalomu alaykum! Bu avtomatik ovozli xizmat. Suhbat yozib olinmoqda.";
        }
        String company = companyName.trim();
        if (en) return "Hello! This is the automated voice assistant for " + company + ". This call is being recorded.";
        return ru
                ? "Здравствуйте! Это автоматический голосовой сервис компании " + company + ". Разговор записывается."
                : "Assalomu alaykum! Bu " + company + " kompaniyasining avtomatik ovozli xizmati. Suhbat yozib olinmoqda.";
    }

    /** Closing line when a guardrail (turn cap, duration cap) ends the call. */
    public static String farewell(String language) {
        if (english(language)) return "Thank you for your time. Goodbye.";
        return russian(language)
                ? "Спасибо за ваше время, до свидания."
                : "Vaqtingiz uchun rahmat, xayr.";
    }

    /** Asked when the model produced no speakable text and the caller is waiting. */
    public static String didNotCatch(String language) {
        if (english(language)) return "I'm sorry, I didn't catch that. Could you please repeat?";
        return russian(language)
                ? "Извините, я вас не расслышал. Повторите, пожалуйста."
                : "Uzr, sizni eshitolmadim. Iltimos, takrorlab ayting.";
    }

    /** Spoken when the agent hands the call to a person. */
    public static String transferring(String language) {
        if (english(language)) return "Please hold on, connecting you to an operator.";
        return russian(language)
                ? "Секунду, я соединю вас с оператором."
                : "Bir daqiqa, sizni operatorga ulab beraman.";
    }

    /** Spoken when the line has gone quiet. */
    public static String stillThere(String language) {
        if (english(language)) return "Hello, are you still there?";
        return russian(language)
                ? "Алло, вы меня слышите?"
                : "Alo, meni eshityapsizmi?";
    }

    /** Technical error line. */
    public static String technicalError(String language) {
        if (english(language)) return "Sorry, a technical error occurred. We will call you back.";
        return russian(language)
                ? "Извините, произошла техническая ошибка. Мы перезвоним вам позже."
                : "Kechirasiz, texnik nosozlik yuz berdi. Sizga keyinroq qayta aloqaga chiqamiz.";
    }

    /**
     * Short "I heard you, I'm working on it" fillers, spoken over the gap while the LLM
     * is still generating (§1.3).
     */
    public static List<String> thinking(String language) {
        if (english(language)) {
            return List.of("One moment.", "Let me check.", "Looking into this.", "Just a second.");
        }
        return russian(language)
                ? List.of("Секунду.", "Сейчас посмотрю.", "Понятно, один момент.", "Сейчас скажу.")
                : List.of("Bir soniya.", "Hozir ko'rib chiqyapman.", "Tushunarli, bir lahza.", "Hozir aytaman.");
    }

    /**
     * Common short opening confirmations pre-warmed into the memory cache.
     */
    public static List<String> confirmations(String language) {
        if (english(language)) {
            return List.of("Alright.", "Understood.", "Got it.", "Sure.", "Thank you.");
        }
        return russian(language)
                ? List.of("Хорошо.", "Понятно.", "Да, конечно.", "Спасибо.")
                : List.of("Aha, tushundim.", "Xo'p bo'ladi.", "Yaxshi.", "Tushunarli.", "Rahmat.");
    }

    /**
     * Resumption prefix for natural conversation flow when resuming a cut-off reply.
     */
    public static String resumptionPrefix(String language) {
        if (english(language)) return "Right, so ";
        return russian(language) ? "Да, так вот, " : "Ha, demak, ";
    }

    /**
     * Every fixed line in one language — what the TTS warm-up pre-synthesizes.
     */
    public static List<String> forLanguage(String language, String companyName) {
        List<String> lines = new ArrayList<>(List.of(
                disclosure(language, companyName),
                farewell(language),
                didNotCatch(language),
                stillThere(language),
                transferring(language),
                technicalError(language)
        ));
        lines.addAll(thinking(language));
        lines.addAll(confirmations(language));
        return List.copyOf(lines);
    }

    private static boolean russian(String language) {
        return language != null && language.startsWith("ru");
    }

    private static boolean english(String language) {
        return language != null && language.startsWith("en");
    }
}
