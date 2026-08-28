package uz.murodjon.uysotvoice.shared.dialog;

import java.util.ArrayList;
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

    /**
     * The §11.1 disclosure: automated system, call is recorded — spoken in the name of
     * the company whose campaign is dialling, since the platform is multi-tenant and a
     * caller is owed the identity of whoever is actually calling them.
     *
     * @param companyName the calling company's name; blank falls back to naming no
     *                    company at all, which is still a valid disclosure — the notice
     *                    is that this is a machine and it records, not who owns it
     */
    public static String disclosure(String language, String companyName) {
        if (companyName == null || companyName.isBlank()) {
            return russian(language)
                    ? "Здравствуйте! Это автоматический голосовой сервис. Разговор записывается."
                    : "Assalomu alaykum! Bu avtomatik ovozli xizmat. Suhbat yozib olinmoqda.";
        }
        String company = companyName.trim();
        return russian(language)
                ? "Здравствуйте! Это автоматический голосовой сервис компании " + company + ". Разговор записывается."
                : "Assalomu alaykum! Bu " + company + " kompaniyasining avtomatik ovozli xizmati. "
                        + "Suhbat yozib olinmoqda.";
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
     * Short "I heard you, I'm working on it" fillers, spoken over the gap while the LLM
     * is still generating (§1.3). A person answering a question does not go silent for a
     * second and a half; they say something like this, and the pause stops feeling like
     * a dropped line.
     *
     * <p>Chosen to be things the model itself would never open a reply with. An
     * acknowledgement ("ha, tushunarli") risks the model beginning its own answer the
     * same way, and the caller then hears it twice. A "give me a second" cannot collide.
     *
     * <p>Kept short on purpose: whatever plays here is queued ahead of the real reply, so
     * a long filler buys the caller company at the cost of delaying the actual answer.
     * More than one so a caller on a long call does not hear the same word every turn.
     */
    public static List<String> thinking(String language) {
        return russian(language)
                ? List.of("Секунду.", "Сейчас посмотрю.", "Понятно, один момент.", "Сейчас скажу.")
                : List.of("Bir soniya.", "Hozir ko'rib chiqyapman.", "Tushunarli, bir lahza.", "Hozir aytaman.");
    }

    /**
     * Every fixed line in one language — what the TTS warm-up pre-synthesizes. Audio is
     * only reusable for the voice of its own language, so warm-up is per language.
     *
     * <p>The fillers matter most here: one that is not already in the cache has to be
     * synthesized inside the very turn it is meant to cover, which is the one situation
     * where it cannot help.
     *
     * @param companyName whose disclosure to include; the rest of the lines name no
     *                    company, so warming a second company only adds that one line
     */
    public static List<String> forLanguage(String language, String companyName) {
        List<String> lines = new ArrayList<>(List.of(disclosure(language, companyName), farewell(language),
                didNotCatch(language), stillThere(language), transferring(language)));
        lines.addAll(thinking(language));
        return List.copyOf(lines);
    }

    private static boolean russian(String language) {
        return language != null && language.startsWith("ru");
    }
}
