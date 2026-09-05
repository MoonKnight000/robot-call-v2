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
            if (en) return "Hello! I am an AI voice robot. This call is being recorded.";
            return ru
                    ? "Здравствуйте! Я голосовой робот. Разговор записывается."
                    : "Assalomu alaykum! Men sun'iy intellekt ovozli robotiman. Suhbat yozib olinmoqda.";
        }
        String company = companyName.trim();
        if (en) return "Hello! I am an AI robot of " + company + ". This call is being recorded.";
        return ru
                ? "Здравствуйте! Я робот компании " + company + ". Разговор записывается."
                : "Assalomu alaykum! Men " + company + " kompaniyasining robotiman. Suhbat yozib olinmoqda.";
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
     *
     * <p>"Wait a moment", never "I understand". The reply that lands behind one of these
     * opens with an acknowledgement of its own, so a filler that also acknowledges is
     * heard as the bot saying the same thing twice — "Tushunarli, bir lahza." followed a
     * second later by "Aha, tushunarli. Murodjon aka, ...". Keep every line here about the
     * waiting and nothing else.
     */
    public static List<String> thinking(String language) {
        if (english(language)) {
            return List.of("One moment.", "Let me check.", "Looking into this.", "Just a second.");
        }
        return russian(language)
                ? List.of("Секунду.", "Сейчас посмотрю.", "Один момент.", "Сейчас скажу.")
                : List.of("Bir soniya.", "Hozir ko'rib chiqyapman.", "Bir lahza.", "Hozir aytaman.");
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
     * The noises a listener makes while somebody else is talking — "keep going", not
     * "my turn". Said quietly over a long answer, they are the difference between a line
     * that is being listened to and one that has gone dead.
     *
     * <p>One or two syllables on purpose: anything longer stops being a backchannel and
     * becomes an interruption, which is the opposite of what it is for.
     */
    public static List<String> backchannels(String language) {
        if (english(language)) {
            return List.of("Mm-hmm.", "Right.", "I see.");
        }
        return russian(language)
                ? List.of("Ага.", "Понятно.", "Да-да.")
                : List.of("Aha.", "Tushunarli.", "Ha-ha.");
    }

    /**
     * The line the bot cuts in with when a caller has been talking long past the point
     * where an operator would have stepped in — an apology and a check, never a demand.
     * The caller stops, the recognizer finally gets its silence, and the turn that follows
     * has something to answer.
     */
    public static String interjection(String language) {
        if (english(language)) return "Sorry to cut in — can I just check I've understood?";
        return russian(language)
                ? "Извините, что перебиваю — можно уточнить, правильно ли я понял?"
                : "Kechirasiz, bo'lganim uchun — to'g'ri tushundimmi, aniqlashtirsam bo'ladimi?";
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
        lines.addAll(backchannels(language));
        lines.add(interjection(language));
        return List.copyOf(lines);
    }

    private static boolean russian(String language) {
        return language != null && language.startsWith("ru");
    }

    private static boolean english(String language) {
        return language != null && language.startsWith("en");
    }
}
