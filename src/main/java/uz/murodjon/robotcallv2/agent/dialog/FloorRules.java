package uz.murodjon.robotcallv2.agent.dialog;

import java.util.Set;

/**
 * The barge-in decisions that do not need the model ({@code LlmTurnClient#isTakingTheFloor}):
 * the bot is paused while that call runs, for up to a second and a half, on every
 * word or two said over it.
 *
 * <p>Only one case is certain from the words alone: a "no", a "wait" or a question word is
 * the caller taking the floor whatever line it was said over. Everything else goes to the
 * model — listener sounds too: "ha" or "mayli" is agreement over one line and the answer
 * over another, and a question the bot asked does not always end in a "?" to tell which.
 */
final class FloorRules {

    /** As {@link Backchannels#words} leaves them: lower case, apostrophes dropped. */
    private static final Set<String> TAKING_THE_FLOOR = Set.of(
            "yoq", "kerakmas", "kerak", "emas", "toxtang", "toxta", "shoshmang", "kuting",
            "nima", "nega", "qanaqa", "qanday", "qachon", "qayer", "qayerda", "kim", "qancha", "nimaga",
            "eshiting", "bilsangiz", "gapim", "gapiray", "savol", "adashdingiz", "notogri", "boshqa",
            "alo", "eshityapsizmi", "gapiryapman",
            "нет", "не", "стоп", "подождите", "погодите", "стойте", "что", "почему", "зачем", "как",
            "когда", "где", "кто", "сколько", "послушайте", "вопрос", "минуточку", "ошиблись",
            "неправильно", "другой", "алло", "слышите", "говорю",
            "no", "stop", "wait", "what", "why");

    private static final Set<String> LISTENING = Set.of(
            "ha", "xa", "haa", "aha", "ahha", "mhm", "mm", "mmm", "hm", "xop", "xopp", "mayli",
            "yaxshi", "tushunarli", "tushundim", "eshitaman", "eshityapman", "albatta", "shunday",
            "togri", "davom", "eting", "gapiravering", "ok", "okey",
            "да", "ага", "угу", "ну", "так", "ясно", "понятно", "хорошо", "конечно", "слушаю",
            "продолжайте", "верно", "точно", "понял", "говорите",
            "yes", "yeah", "uh", "huh");

    private FloorRules() {
    }

    /**
     * Whether every word in {@code text} is a listener sound (backchannel).
     * Used by {@code ClientInputGate#saidEnoughToInterrupt} to prevent filler-only
     * utterances from triggering a barge-in even when the word count meets the threshold.
     *
     * @param text the caller's words, may be null or blank
     * @return true when all words are in the LISTENING set; false when the text is empty
     *         or contains at least one word not in either set
     */
    public static boolean isBackchannel(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String[] words = Backchannels.words(text);
        if (words.length == 0) {
            return false;
        }
        for (String word : words) {
            if (!LISTENING.contains(word)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param callerText the caller's words
     * @return true when the caller is taking the floor, null when only the model can tell
     */
    static Boolean decide(String callerText) {
        if (callerText == null || callerText.isBlank()) {
            return null;
        }
        if (callerText.contains("?")) {
            return true;
        }
        for (String word : Backchannels.words(callerText)) {
            if (TAKING_THE_FLOOR.contains(word)) {
                return true;
            }
        }
        return null;
    }
}
