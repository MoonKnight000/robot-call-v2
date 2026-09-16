package uz.murodjon.robotcallv2.agent.dialog;

import java.util.Set;

/**
 * A caller's plain yes — what lets a write tool's request go out once it was read back to
 * them (UNIVERSAL-OPERATOR-PLAN B.3).
 *
 * <p>Matched the way {@link Farewells} matches a sign-off: a short transcript made of
 * nothing but these words, at least one of them a yes. Short on purpose: "ha, lekin 2 ta"
 * is a correction, not a confirmation, and a correction must never send the order that
 * was read back. A negation anywhere makes it a no, whatever else was said.
 */
final class Affirmations {

    /** Longest answer that can still be nothing but a yes ("ha, hammasi to'g'ri aka"). */
    private static final int MAX_WORDS = 4;

    /** As {@link Backchannels#words} leaves them: lower case, apostrophes dropped ("to'g'ri" → "togri"). */
    private static final Set<String> YES = Set.of(
            "ha", "xa", "haa", "xop", "bopti", "boladi", "mayli", "yaxshi", "albatta", "togri",
            "tasdiqlayman", "roziman", "ok", "okey",
            "да", "ага", "верно", "правильно", "подтверждаю", "согласен", "согласна", "конечно",
            "хорошо", "давайте", "давай",
            "yes", "yeah", "correct");

    /** Words that may keep a yes company without carrying it. */
    private static final Set<String> FILLERS = Set.of(
            "aka", "opa", "endi", "hammasi", "shunday", "shunaqa", "juda", "rahmat",
            "всё", "все", "так", "вот", "спасибо");

    private static final Set<String> NO = Set.of("yoq", "emas", "kerakmas", "нет", "не", "no", "not");

    private Affirmations() {
    }

    /** Whether a caller transcript is a yes and nothing else. */
    static boolean saidByCaller(String text) {
        if (text == null) {
            return false;
        }
        String[] words = Backchannels.words(text);
        if (words.length == 0 || words.length > MAX_WORDS) {
            return false;
        }
        boolean yes = false;
        for (String word : words) {
            if (NO.contains(word)) {
                return false;
            }
            if (YES.contains(word)) {
                yes = true;
            } else if (!FILLERS.contains(word)) {
                return false;
            }
        }
        return yes;
    }
}
