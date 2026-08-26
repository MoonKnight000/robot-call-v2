package uz.murodjon.uysotvoice.agent.dialog;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads Uzbek number words back into numbers — the counterpart of {@code
 * agent.tts.UzbekNumberWords}, which writes them out.
 *
 * <p>Exists for one reason: {@link FactGuard} compares digits, so a sum the agent spells
 * out passes it untouched. That was an acceptable gap while the guard could still
 * withhold a sentence before it was spoken, because the prompt asked for digits and got
 * them. It stopped being acceptable on realtime calls, where the guard is an audit of
 * words already heard and there is no synthesis step for a "write sums as digits" rule to
 * serve — the transcript comes back however the engine happened to say it.
 *
 * <p>Package-private, next to its only caller, for the same reason {@code
 * UzbekNumberWords} is package-private next to {@code SpeechTextNormalizer}: deciding
 * when a run of words is a quantity is the guard's business, not a general utility.
 */
final class UzbekNumberParser {

    /** Units, tens and scales, all in one table — a token is looked up once. */
    private static final Map<String, long[]> WORDS = new LinkedHashMap<>();

    /**
     * Marks a table entry as a scale word ({@code yuz}, {@code ming}, ...) rather than a
     * plain count: {@code [value, isScale]}.
     */
    private static final int IS_SCALE = 1;

    static {
        // "nol" is deliberately absent. It never occurs inside a spoken quantity — nobody
        // says "besh ming nol" — only in a reference number read digit by digit. Leaving
        // it out makes it break the run instead of extending it, which is what keeps
        // "...besh yuz ming" and the contract number read out after it from being added
        // together into a figure neither of them is.
        String[] ones = {"bir", "ikki", "uch", "to'rt", "besh", "olti", "yetti", "sakkiz", "to'qqiz"};
        for (int i = 0; i < ones.length; i++) {
            WORDS.put(ones[i], new long[]{i + 1, 0});
        }
        String[] tens = {"o'n", "yigirma", "o'ttiz", "qirq", "ellik", "oltmish", "yetmish", "sakson", "to'qson"};
        for (int i = 0; i < tens.length; i++) {
            WORDS.put(tens[i], new long[]{(i + 1) * 10L, 0});
        }
        WORDS.put("yuz", new long[]{100L, 1});
        WORDS.put("ming", new long[]{1_000L, 1});
        WORDS.put("million", new long[]{1_000_000L, 1});
        WORDS.put("milliard", new long[]{1_000_000_000L, 1});
    }

    /** Word characters plus every apostrophe a keyboard or a transcriber might produce. */
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{M}'‘’ʻʼ`´]+");

    private UzbekNumberParser() {
    }

    /**
     * Every spelled-out number in {@code text}, as the words that spell it.
     *
     * <p>A bare scale word on its own is deliberately not a number here. "ming rahmat" is
     * ordinary politeness, not a claim about a thousand of anything, and treating it as
     * one would hand perfectly good calls to a human operator. Any real sum a debt call
     * states is counted — "besh ming", "bir million" — and a genuine debt of exactly one
     * thousand so'm is below the scale the guard cares about in the first place.
     */
    static List<SpelledNumber> findAll(String text) {
        List<SpelledNumber> found = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return found;
        }
        Matcher m = TOKEN.matcher(text);
        List<String> span = new ArrayList<>();
        long total = 0;
        long current = 0;
        int countedTokens = 0;
        while (m.find()) {
            String token = normalize(m.group());
            long[] entry = WORDS.get(token);
            if (entry == null) {
                flush(found, span, total + current, countedTokens);
                span = new ArrayList<>();
                total = 0;
                current = 0;
                countedTokens = 0;
                continue;
            }
            span.add(m.group());
            if (entry[IS_SCALE] == 0) {
                current += entry[0];
                countedTokens++;
                continue;
            }
            long count = current == 0 ? 1 : current;
            if (entry[0] == 100L) {
                // "yuz" multiplies within the group; "ming" and up close it out.
                current = count * 100L;
            } else {
                total += count * entry[0];
                current = 0;
            }
            countedTokens++;
        }
        flush(found, span, total + current, countedTokens);
        return found;
    }

    /** A finished span becomes a number only if it counted more than one word. */
    private static void flush(List<SpelledNumber> found, List<String> span, long value, int countedTokens) {
        if (span.isEmpty() || countedTokens < 2 || value <= 0) {
            return;
        }
        found.add(new SpelledNumber(String.join(" ", span), BigDecimal.valueOf(value)));
    }

    /**
     * Lower-cases, folds every apostrophe variant onto {@code '}, and drops an ordinal
     * suffix. A date is read aloud as "ikki ming yigirma oltinchi", and the suffix is the
     * only thing between that and the year the guard already knows how to excuse.
     */
    private static String normalize(String token) {
        String lower = token.toLowerCase()
                .replace('‘', '\'').replace('’', '\'')
                .replace('ʻ', '\'').replace('ʼ', '\'')
                .replace('`', '\'').replace('´', '\'');
        if (lower.endsWith("inchi")) {
            return lower.substring(0, lower.length() - "inchi".length());
        }
        if (lower.endsWith("nchi")) {
            return lower.substring(0, lower.length() - "nchi".length());
        }
        return lower;
    }
}
