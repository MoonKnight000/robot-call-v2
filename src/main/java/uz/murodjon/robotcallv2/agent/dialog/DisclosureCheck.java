package uz.murodjon.robotcallv2.agent.dialog;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Whether the §11.1 disclosure was actually said, judged from the transcript of what
 * the bot said.
 *
 * <p>Only a realtime call needs this. The cascade pipeline speaks the disclosure from
 * code, so it is either said or the call never started; a speech-to-speech engine is only
 * <em>asked</em> to say it, in its instructions, and a caller who says "alo?" over the
 * greeting cuts it off mid-sentence. The legal requirement does not care why it was not
 * heard.
 *
 * <p>Deliberately forgiving: the engine paraphrases, and its own transcription of Uzbek
 * misspells words. So the test is whether most of the disclosure's content words turn
 * up, matched on their stems — a false "not said" only costs one extra sentence, a false
 * "said" is the failure being guarded against, and a threshold of half is where the two
 * balance on recorded calls.
 */
public final class DisclosureCheck {

    /** Words shorter than this are grammar ("va", "bu", "это"), not content. */
    private static final int MIN_WORD_LENGTH = 5;
    /** Uzbek and Russian inflect at the end; the front of a word survives both. */
    private static final int STEM_LENGTH = 5;
    private static final double REQUIRED_SHARE = 0.5;

    private DisclosureCheck() {
    }

    /**
     * @param disclosure the line the engine was told to say; null or blank means there
     *                   was nothing to say, which is trivially covered
     * @param spoken     what the bot is transcribed as having said so far
     */
    public static boolean covered(String disclosure, String spoken) {
        Set<String> stems = stems(disclosure);
        if (stems.isEmpty()) {
            return true;
        }
        if (spoken == null || spoken.isBlank()) {
            return false;
        }
        String heard = normalize(spoken);
        long found = stems.stream().filter(heard::contains).count();
        return found >= Math.ceil(stems.size() * REQUIRED_SHARE);
    }

    private static Set<String> stems(String text) {
        Set<String> stems = new LinkedHashSet<>();
        if (text == null) {
            return stems;
        }
        for (String word : normalize(text).split("\\s+")) {
            if (word.length() >= MIN_WORD_LENGTH) {
                stems.add(word.substring(0, STEM_LENGTH));
            }
        }
        return stems;
    }

    /**
     * Lower case, one apostrophe for the four the Uzbek Latin alphabet is written with,
     * and no punctuation — so "Qo‘ng‘iroq," and "qo'ng'iroq" compare equal.
     */
    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replace('‘', '\'').replace('’', '\'').replace('ʻ', '\'').replace('`', '\'')
                .replaceAll("[^\\p{L}\\p{N}'\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
