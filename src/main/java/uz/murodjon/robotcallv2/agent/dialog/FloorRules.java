package uz.murodjon.robotcallv2.agent.dialog;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The barge-in decisions that do not need the model ({@code LlmTurnClient#isTakingTheFloor}):
 * the bot is paused while that call runs, for up to a second and a half, on every
 * word or two said over it.
 *
 * <p>Only one case is certain from the words alone: a "no", a "wait" or a question word is
 * the caller taking the floor whatever line it was said over. Everything else goes to the
 * model — listener sounds too: "ha" or "mayli" is agreement over one line and the answer
 * over another, and a question the bot asked does not always end in a "?" to tell which.
 *
 * <p>Matched by form, not only by whole word: every dialect says "no" its own way
 * ("yo'q", "yo'g'-e", "yuq"), but Uzbek builds a refusal and a question with the same
 * suffixes everywhere ("to'lamayman", "shoshmanglar", "bilasizmi"). A word none of this
 * catches is not a wrong answer — it only costs the model call, and
 * {@link FloorLearning} picks up the ones callers keep saying.
 */
final class FloorRules {

    /** As {@link Backchannels#words} leaves them: lower case, apostrophes dropped. */
    private static final Set<String> TAKING_THE_FLOOR = Set.of(
            "kerak", "emas", "kim", "kimsiz", "eshiting", "bilsangiz", "gapim", "gapiray", "gapiryapman",
            "boshqa", "alo",
            "нет", "не", "стоп", "что", "когда", "где", "кто", "вопрос", "другой", "алло", "говорю",
            "no", "stop", "wait", "what", "why");

    /**
     * Word beginnings that take the floor in any form: "to'xtanglar", "nimaga", "qayerdan",
     * "savolim", "подождите", "секундочку". None of them begins a listener sound —
     * "gapir" is left out on purpose, "gapiravering" is "go on".
     */
    private static final List<String> STEMS = List.of(
            "toxta", "shoshma", "kuting", "daqiqa", "sekund", "minut", "savol",
            "nima", "nega", "qanaqa", "qanday", "qachon", "qayer", "qancha", "nechta", "nechi",
            "adashdi", "notogri", "eshityapsiz",
            "подожд", "погод", "стой", "секунд", "минут", "почему", "зачем", "сколько", "как",
            "ошибл", "неправ", "слыш", "послуш");

    /**
     * Uzbek negative verb endings: "shoshmang", "to'lamayman", "olmadim", "kerakmas",
     * "tushunmadim". A word has to be longer than its ending, so "mas" alone is no match.
     */
    private static final List<String> NEGATIVE_ENDINGS = List.of(
            "mang", "manglar", "mayman", "maymiz", "maysiz", "maydi", "madim", "madik", "madi",
            "magan", "masman", "mas");

    /** Uzbek question endings a recognizer often writes without the "?": "bilasizmi", "keldimi", "bormikan". */
    private static final List<String> QUESTION_ENDINGS = List.of(
            "sizmi", "mizmi", "manmi", "dimi", "mikan");

    /** "yo'q" as the dialects say it, apostrophes and hyphen already gone: "yoq", "yoqe", "yog" (of "yo'g'-e"), "yuq", "йўқ". */
    private static final Pattern NO = Pattern.compile("(yo|yu)[qg](e|ey)?|й[ўуо][қкғг]е?");

    private static final Set<String> LISTENING = Set.of(
            "ha", "xa", "haa", "aha", "ahha", "mhm", "mm", "mmm", "hm", "xop", "xopp", "mayli",
            "yaxshi", "tushunarli", "tushundim", "eshitaman", "eshityapman", "albatta", "shunday",
            "togri", "davom", "eting", "gapiravering", "boladi", "kelishdik", "ok", "okey",
            "да", "ага", "угу", "ну", "так", "ясно", "понятно", "хорошо", "конечно", "слушаю",
            "продолжайте", "верно", "точно", "понял", "говорите", "договорились",
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
            if (takesTheFloor(word)) {
                return true;
            }
        }
        return null;
    }

    private static boolean takesTheFloor(String word) {
        if (TAKING_THE_FLOOR.contains(word) || NO.matcher(word).matches()) {
            return true;
        }
        for (String stem : STEMS) {
            if (word.startsWith(stem)) {
                return true;
            }
        }
        return endsWithAny(word, NEGATIVE_ENDINGS) || endsWithAny(word, QUESTION_ENDINGS);
    }

    /** Longer than the ending by two letters at least, so the ending is a suffix and not the word. */
    private static boolean endsWithAny(String word, List<String> endings) {
        for (String ending : endings) {
            if (word.length() >= ending.length() + 2 && word.endsWith(ending)) {
                return true;
            }
        }
        return false;
    }
}
