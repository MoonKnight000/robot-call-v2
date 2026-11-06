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

    /**
     * The recording half of the §11.1 disclosure on its own — what a {@code HUMAN_LIKE}
     * agent says. Its company was allowed an agent that does not say it is a machine;
     * that the call is recorded is owed to every caller.
     */
    public static String recordingNotice(String language) {
        if (english(language)) return "This call is being recorded for quality purposes.";
        return russian(language)
                ? "Разговор записывается для контроля качества."
                : "Suhbat sifat nazorati uchun yozib olinmoqda.";
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

    /** The reply to a caller checking the line ("Alo?", "Eshityapsizmi?"), before the agent's last question. */
    public static String lineCheckReply(String language) {
        if (english(language)) return "Yes, I can hear you.";
        return russian(language) ? "Да, я вас слышу." : "Ha, eshitaman.";
    }

    /** Spoken when the agent hands the call to a person. */
    public static String transferring(String language) {
        if (english(language)) return "Please hold on, connecting you to an operator.";
        return russian(language)
                ? "Секунду, я соединю вас с оператором."
                : "Bir daqiqa, sizni operatorga ulab beraman.";
    }

    /**
     * Spoken when the caller thanks the agent or gives a final closing gratitude.
     */
    public static String gratitudeReply(String language) {
        if (english(language)) return "You're welcome! Have a great day, goodbye.";
        return russian(language)
                ? "Пожалуйста! Всего доброго, до свидания."
                : "Arzimaydi! Salomat bo'ling, xayr.";
    }

    /** Spoken before hanging up on a caller who asked never to be called again. */
    public static String doNotCallReply(String language) {
        if (english(language)) return "Understood, your number will be removed from our list. Sorry for the trouble.";
        return russian(language)
                ? "Понятно, ваш номер исключен из базы. Извините за беспокойство."
                : "Tushundim, raqamingiz ro'yxatdan chiqariladi, boshqa qo'ng'iroq qilmaymiz. Bezovta qilganimiz uchun uzr.";
    }

    /** Spoken when the line has gone quiet. */
    public static String stillThere(String language) {
        if (english(language)) return "Hello, are you still there?";
        return russian(language)
                ? "Алло, вы меня слышите?"
                : "Alo, meni eshityapsizmi?";
    }

    /** The last check on a quiet line, which says the call will end if it stays quiet. */
    public static String stillThereLastCall(String language) {
        if (english(language)) return "Are you still on the line? If I don't hear from you, I'll end the call.";
        return russian(language)
                ? "Вы на связи? Если вы не ответите, я завершу звонок."
                : "Aloqadamisiz? Javob bo'lmasa, qo'ng'iroqni yakunlayman.";
    }

    /**
     * First line of a call that lost its speech-to-speech engine mid-conversation and
     * carries on in the cascade pipeline — in a different voice, so it has to be said.
     */
    public static String reconnected(String language) {
        if (english(language)) return "Sorry, the line dropped for a moment. Let's continue.";
        return russian(language)
                ? "Извините, связь на секунду прервалась. Продолжим."
                : "Kechirasiz, aloqada kichik uzilish bo'ldi. Suhbatimizni davom ettiramiz.";
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
     *
     * <p>No promise of an action either. The filler is armed before the LLM has decided
     * anything, and most replies behind it are a plain question: "Hozir tekshirib ko'raman."
     * followed by "Ajoyib! Necha xonali uy qidiryapsiz?" checked nothing. A real lookup
     * announces itself through {@link PreToolPhrases}. "Bir daqiqa" is out too — a minute
     * is a promise of a long wait, and scenario guardrails ban it.
     */
    public static List<String> thinking(String language) {
        if (english(language)) {
            return List.of("One moment.", "Just a second.");
        }
        return russian(language)
                ? List.of("Секунду.", "Один момент.")
                : List.of("Bir soniya.", "Bir lahza.");
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
                : List.of("Aha.", "Tushunarli.", "Eshityapman.");
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
     * Every fixed line in one language — what the TTS warm-up pre-synthesizes.
     */
    public static List<String> forLanguage(String language, String companyName) {
        List<String> lines = new ArrayList<>(List.of(
                disclosure(language, companyName),
                recordingNotice(language),
                farewell(language),
                didNotCatch(language),
                stillThere(language),
                stillThereLastCall(language),
                transferring(language),
                gratitudeReply(language),
                doNotCallReply(language),
                reconnected(language)
        ));
        lines.addAll(thinking(language));
        lines.addAll(backchannels(language));
        lines.addAll(PreToolPhrases.allPhrases(language));
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
