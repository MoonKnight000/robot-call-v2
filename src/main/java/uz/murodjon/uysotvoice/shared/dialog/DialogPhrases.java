package uz.murodjon.uysotvoice.shared.dialog;

import java.util.List;

/**
 * The lines the agent speaks from code rather than from the model: the §11.1
 * disclosure, the goodbye a guardrail closes on, and the "say that again" fallback
 * used when the LLM returns nothing speakable.
 *
 * <p>They live here, outside the dialog engine, because two components need the exact
 * same strings. The engine speaks them; the TTS warm-up synthesizes them at startup so
 * they are already in the cache when the first call lands. A copy that drifts by one
 * character is a cache key that never hits, so there is one copy.
 */
public final class DialogPhrases {

    private DialogPhrases() {
    }

    /** The §11.1 disclosure: automated system, call is recorded. */
    public static String disclosure(String language) {
        return russian(language)
                ? "Здравствуйте! Это автоматический голосовой сервис компании Uysot. Разговор записывается."
                : "Assalomu alaykum! Bu Uysot kompaniyasining avtomatik ovozli xizmati. Suhbat yozib olinmoqda.";
    }

    /** Closing line when a guardrail (turn cap, duration cap) ends the call. */
    public static String farewell(String language) {
        return russian(language)
                ? "Спасибо за ваше время, до свидания."
                : "Vaqtingiz uchun rahmat, xayr.";
    }

    /** Asked when the model produced no speakable text and the caller is waiting. */
    public static String didNotCatch(String language) {
        return russian(language)
                ? "Извините, я вас не расслышал. Повторите, пожалуйста."
                : "Uzr, sizni eshitolmadim. Iltimos, takrorlab ayting.";
    }

    /**
     * Spoken when the agent hands the call to a person — either because the client asked
     * for one, or because a guardrail stopped the agent from answering (§4.4, §11.6).
     */
    public static String transferring(String language) {
        return russian(language)
                ? "Секунду, я соединю вас с оператором."
                : "Bir daqiqa, sizni operatorga ulab beraman.";
    }

    /**
     * Spoken when the line has gone quiet — nobody is talking and nothing is going to
     * happen until one side does. The engine only ever reaches a turn on a final
     * transcript, so without this a silent caller (or a recognizer that never emits a
     * final) leaves both ends listening to nothing until the duration cap fires.
     */
    public static String stillThere(String language) {
        return russian(language)
                ? "Алло, вы меня слышите?"
                : "Alo, meni eshityapsizmi?";
    }

    /**
     * Every fixed line in one language — what the TTS warm-up pre-synthesizes. Audio is
     * only reusable for the voice of its own language, so warm-up is per language.
     */
    public static List<String> forLanguage(String language) {
        return List.of(disclosure(language), farewell(language), didNotCatch(language),
                stillThere(language), transferring(language));
    }

    private static boolean russian(String language) {
        return language != null && language.startsWith("ru");
    }
}
