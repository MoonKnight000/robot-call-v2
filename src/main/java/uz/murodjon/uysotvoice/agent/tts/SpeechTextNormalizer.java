package uz.murodjon.uysotvoice.agent.tts;

import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * Rewrites the digits in a line into the words a person would say, before it is handed
 * to a TTS provider.
 *
 * <p>The providers normalize numerals for the languages they were built for; Uzbek is
 * not one of them. A sum, a due date and a contract number are exactly what a debt call
 * is about, and they came out as "UY minus ikki ming yigirma olti chiziq…" — the caller
 * hears punctuation read aloud and cannot tell what they owe or by when. Three shapes
 * need different readings, so the passes run in order:
 *
 * <ol>
 *   <li>an ISO date ({@code 2026-07-01}) — a date, not three numbers;</li>
 *   <li>a reference number ({@code UY-2026-00123-sonli}) — read digit by digit, since
 *       its leading zeros are part of the label and its hyphens are not spoken;</li>
 *   <li>{@code <digits>-<word>} ({@code 2026-yil}, {@code 1-iyul}) — an ordinal;</li>
 *   <li>anything left ({@code 1500000}) — a quantity.</li>
 * </ol>
 *
 * <p>Applied at the TTS boundary rather than to the model's reply, so the transcript,
 * the live feed and {@code FactGuard} all keep seeing the figures as digits — the guard
 * compares them against the call's facts, and it can only do that on digits.
 *
 * <p>Uzbek only. Russian numerals are read correctly by the providers already, and
 * spelling them out here would mean getting Russian case and gender agreement right for
 * no gain.
 */
public final class SpeechTextNormalizer {

    /** {@code 2026-07-01} — the shape a date fact arrives in. */
    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b");

    /**
     * A reference number: either three or more hyphen-joined parts ({@code
     * UY-2026-00123}), or a letter prefix on digits ({@code UY-00123}). Deliberately
     * narrower than "anything with a hyphen" so {@code 2026-yil} — one hyphen, digits
     * first — is left to the ordinal pass below.
     */
    private static final Pattern REFERENCE = Pattern.compile(
            "\\b[\\p{L}\\d]+(?:-[\\p{L}\\d]+){2,}\\b|\\b\\p{L}+-\\d[\\p{L}\\d]*\\b");

    /** {@code 2026-yil}, {@code 1-iyul}, {@code 7-avgustga} — a number used as an ordinal. */
    private static final Pattern ORDINAL = Pattern.compile("(\\d+)-(\\p{L}+)");

    /** A quantity, with the separators a model writes a sum with: {@code 1 500 000}, {@code 1.500.000}. */
    private static final Pattern QUANTITY = Pattern.compile("\\d[\\d\\s.,\\u00A0']*\\d|\\d");

    /**
     * Longest digit run still read as a quantity — twelve digits is 999 milliard so'm,
     * more than any debt this agent calls about. Above it the run is an identifier (a
     * card or account number) and is read out digit by digit instead.
     */
    private static final int MAX_QUANTITY_DIGITS = 12;

    private static final String[] MONTHS = {
            "yanvar", "fevral", "mart", "aprel", "may", "iyun",
            "iyul", "avgust", "sentabr", "oktabr", "noyabr", "dekabr"
    };

    private SpeechTextNormalizer() {
    }

    /**
     * {@code text} with its digits written out, or {@code text} unchanged when the
     * language is not Uzbek or there is nothing to rewrite.
     */
    public static String normalize(String text, String language) {
        if (text == null || text.isBlank() || !uzbek(language)) {
            return text;
        }
        String out = ISO_DATE.matcher(text).replaceAll(SpeechTextNormalizer::isoDate);
        out = REFERENCE.matcher(out).replaceAll(m -> reference(m.group()));
        out = ORDINAL.matcher(out).replaceAll(
                m -> UzbekNumberWords.ordinal(Long.parseLong(m.group(1))) + " " + m.group(2));
        return QUANTITY.matcher(out).replaceAll(m -> quantity(m.group()));
    }

    private static boolean uzbek(String language) {
        return language != null && language.toLowerCase().startsWith("uz");
    }

    /** {@code 2026-07-01} → "ikki ming yigirma oltinchi yil birinchi iyul". */
    private static String isoDate(MatchResult m) {
        int month = Integer.parseInt(m.group(2));
        if (month < 1 || month > 12) {
            return m.group(); // not a date after all — leave it to the passes below
        }
        return UzbekNumberWords.ordinal(Long.parseLong(m.group(1))) + " yil "
                + UzbekNumberWords.ordinal(Long.parseLong(m.group(3))) + " " + MONTHS[month - 1];
    }

    /** Hyphens dropped, digit parts read out one digit at a time, letter parts left alone. */
    private static String reference(String token) {
        StringBuilder sb = new StringBuilder();
        for (String part : token.split("-")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(part.chars().allMatch(Character::isDigit) ? UzbekNumberWords.digits(part) : part);
        }
        return sb.toString();
    }

    /**
     * A written number in words, or the text as-is when it is not a plain integer —
     * a decimal or a stray "12.5.3" is better left for the provider to attempt than
     * turned into a wrong sum.
     */
    private static String quantity(String written) {
        String cleaned = written.replaceAll("[\\s\\u00A0']", "");
        if (cleaned.matches("\\d{1,3}([.,]\\d{3})+")) {
            cleaned = cleaned.replaceAll("[.,]", ""); // grouping, not a decimal point
        }
        if (!cleaned.matches("\\d+")) {
            return written;
        }
        return cleaned.length() > MAX_QUANTITY_DIGITS
                ? UzbekNumberWords.digits(cleaned)
                : UzbekNumberWords.cardinal(Long.parseLong(cleaned));
    }
}
