package uz.murodjon.robotcallv2.agent.dialog;

import java.util.Set;

/**
 * The noises a caller makes to show they are still listening, as opposed to anything they
 * expect an answer to.
 *
 * <p>A person says "aha" over the top of whoever is talking and expects them to carry on.
 * The VAD cannot tell that from a real interruption — it hears speech either way — so the
 * bot goes silent, the recognizer turns the "aha" into a final, and a final starts a turn.
 * The caller then gets a fresh question instead of the rest of the sentence they were
 * agreeing with, and whatever the bot was in the middle of saying is never finished.
 *
 * <p>Two conditions together, because either alone is wrong. On its own a word count would
 * swallow "yo'q" and "to'ladim", which are one word and are the whole answer. On its own a
 * word list would swallow "ha, ertaga to'layman", where the "ha" is the start of a
 * sentence. So: short <em>and</em> nothing but these words.
 *
 * <p>uz and ru in one set on purpose — the two lists do not collide, and a caller answering
 * a uz-UZ call in Russian is ordinary here.
 */
final class Backchannels {

    private static final Set<String> WORDS = Set.of(
            "ha", "aha", "ahan", "uhu", "uhum", "hm", "hmm", "mm", "xop", "xo'p", "bo'pti", "bopti",
            "mayli", "yaxshi", "tushunarli", "tushundim", "shundaymi", "shunaqami",
            "да", "ага", "угу", "мм", "так", "ясно", "понятно", "хорошо", "ладно", "давай",
            "понял", "поняла");

    private Backchannels() {
    }

    /**
     * Whether {@code text} is the caller agreeing rather than speaking.
     *
     * @param minWords utterances of at least this many words are always taken as speech,
     *                 whatever they are made of. 0 turns the whole check off
     */
    static boolean matches(String text, int minWords) {
        if (text == null || minWords <= 0) {
            return false;
        }
        String[] words = words(text);
        if (words.length == 0 || words.length >= minWords) {
            return false;
        }
        for (String word : words) {
            if (!WORDS.contains(word)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Lowercased words, with the apostrophes of {@code xo'p} and {@code o'ldi} dropped
     * rather than treated as breaks — otherwise a single Uzbek word arrives here as two.
     *
     * <p>Shared with {@link Farewells}, which reads caller transcripts under the same
     * contract and has to split them exactly the same way.
     */
    static String[] words(String text) {
        StringBuilder normalized = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));
            // Before the letter test, not after: the Unicode apostrophes an Uzbek keyboard
            // produces are modifier LETTERS, so a letter-first test keeps them and leaves
            // "xoʻp" as a word this never matches.
            if (isApostrophe(c)) {
                continue;
            }
            if (Character.isLetterOrDigit(c)) {
                normalized.append(c);
            } else if (!normalized.isEmpty() && normalized.charAt(normalized.length() - 1) != ' ') {
                normalized.append(' ');
            }
        }
        return normalized.toString().trim().split(" ");
    }

    private static boolean isApostrophe(char c) {
        return c == '\'' || c == '`' || c == '‘' || c == '’' || c == 'ʻ' || c == 'ʼ';
    }
}
