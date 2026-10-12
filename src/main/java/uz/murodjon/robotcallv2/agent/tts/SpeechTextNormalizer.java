package uz.murodjon.robotcallv2.agent.tts;

import uz.murodjon.robotcallv2.aiagent.domain.entity.PronunciationRule;

import java.time.LocalTime;
import java.util.List;
import java.util.function.LongFunction;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * Rewrites digits, currencies, percentages, and dates into spoken words across multiple
 * languages (Uzbek Latin & Cyrillic, Russian, English) before handing text to a TTS provider.
 *
 * <p>Applied at the TTS boundary rather than to the LLM's model reply, so transcripts
 * and FactGuard keep seeing digits and structured numbers intact.</p>
 */
public final class SpeechTextNormalizer {

    /** {@code 2026-07-01} or {@code 01.07.2026} \u2014 date formats. */
    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b");
    private static final Pattern DOT_DATE = Pattern.compile("\\b(\\d{2})\\.(\\d{2})\\.(\\d{4})\\b");

    /** Percentage like {@code 15%}, {@code 20 %}. */
    private static final Pattern PERCENTAGE = Pattern.compile("(\\d+)\\s*%");

    /** Currency like {@code $1500}, {@code 1500 USD}, {@code 1 500 000 so'm}. */
    private static final Pattern UZ_SUM = Pattern.compile("(?i)(\\d[\\d\\s.,\\u00A0']*\\d|\\d+)\\s*(so['`’‘]m|som|сўм|сум|uzs)\\b");
    private static final Pattern RU_RUBLE = Pattern.compile("(?i)(\\d[\\d\\s.,\\u00A0']*\\d|\\d+)\\s*(руб(?:ля|лей|\\.)?|rub)\\b");
    private static final Pattern USD_AMOUNT = Pattern.compile("(?i)(?:\\$\\s*(\\d[\\d\\s.,\\u00A0']*\\d|\\d+)|(\\d[\\d\\s.,\\u00A0']*\\d|\\d+)\\s*(?:usd|dollar[s]?|доллар(?:ов|а)?))");

    /**
     * A reference or contract number: hyphen-joined parts or letter prefix on digits.
     */
    private static final Pattern REFERENCE = Pattern.compile(
            "\\b[\\p{L}\\d]+(?:-[\\p{L}\\d]+){2,}\\b|\\b\\p{L}+-\\d[\\p{L}\\d]*\\b");

    /** {@code 2026-yil}, {@code 1-iyul}, {@code 7-avgustga} \u2014 Uzbek ordinal. */
    private static final Pattern UZ_ORDINAL = Pattern.compile("(\\d+)-(\\p{L}+)");

    /** A quantity with grouping separators: {@code 1 500 000}, {@code 1.500.000}. */
    private static final Pattern QUANTITY = Pattern.compile("\\d[\\d\\s.,\\u00A0']*\\d|\\d");

    /**
     * A phone number, either with its country code ({@code +998 90 123 45 67},
     * {@code 998901234567}) or as the local nine digits written in the groups they are said
     * in ({@code 90 123 45 67}).
     *
     * <p>A bare {@code 901234567} is deliberately not here. Nothing marks it as a phone
     * number rather than a reference, and reading an order number out in pairs is the same
     * mistake in the other direction.
     */
    private static final Pattern PHONE = Pattern.compile(
            "(?<![\\d+])\\+?998[\\s\\-]?\\d{2}[\\s\\-]?\\d{3}[\\s\\-]?\\d{2}[\\s\\-]?\\d{2}(?!\\d)"
                    + "|(?<!\\d)\\d{2}[\\s\\-]\\d{3}[\\s\\-]\\d{2}[\\s\\-]\\d{2}(?!\\d)");

    /**
     * A clock time: {@code 9:00}, {@code 14:30}. The hour a slot or a callback is set for —
     * without this the quantity rule reads the two halves as separate figures either side
     * of a colon the synthesizer then has to guess at.
     *
     * <p>A second colon on either side rules the match out, so {@code 14:00:00} inside an
     * ISO timestamp is left alone rather than half-converted: a tool's raw
     * {@code startsAt} is not something to read aloud in the first place.
     */
    private static final Pattern CLOCK = Pattern.compile("(?<![\\d:])([01]?\\d|2[0-3]):([0-5]\\d)(?![\\d:])");

    /**
     * A stage direction in square brackets — {@code [laugh]}, {@code [sound concerned]} —
     * the delivery tags some voices act out. Letters and spaces only, so a system-note
     * echo ({@code [SYSTEM: ...]}) is not mistaken for one; that is dropped whole upstream.
     */
    private static final Pattern DELIVERY_TAG = Pattern.compile("\\[[\\p{L} ]{1,40}]\\s*");

    private static final int MAX_QUANTITY_DIGITS = 12;

    private static final String[] UZ_MONTHS = {
            "yanvar", "fevral", "mart", "aprel", "may", "iyun",
            "iyul", "avgust", "sentabr", "oktabr", "noyabr", "dekabr"
    };

    private static final String[] RU_MONTHS = {
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"
    };

    private static final String[] EN_MONTHS = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
    };

    private SpeechTextNormalizer() {
    }

    /**
     * Normalizes {@code text} into spoken words according to the target {@code language}.
     */
    /**
     * Normalizes {@code text} into spoken words according to the target {@code language},
     * additionally applying any agent-level custom pronunciation rules.
     */
    public static String normalize(String text, String language, List<PronunciationRule> rules) {
        String base = normalize(text, language);
        return applyPronunciationRules(base, language, rules);
    }

    /**
     * Replaces configured acronyms, domain jargon, or custom phonetic words.
     */
    public static String applyPronunciationRules(String text, String language, List<PronunciationRule> rules) {
        if (text == null || text.isBlank() || rules == null || rules.isEmpty()) {
            return text;
        }
        String result = text;
        for (PronunciationRule rule : rules) {
            if (rule.word() == null || rule.word().isBlank() || rule.replacement() == null) {
                continue;
            }
            if (rule.language() != null && !rule.language().isBlank() && !"*".equals(rule.language())) {
                if (language != null && !language.toLowerCase().startsWith(rule.language().toLowerCase())) {
                    continue;
                }
            }
            int flags = rule.caseSensitive() ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
            String regex = "\\b" + Pattern.quote(rule.word().trim()) + "\\b";
            result = Pattern.compile(regex, flags).matcher(result).replaceAll(rule.replacement().trim());
        }
        return result;
    }

    public static String normalize(String text, String language) {
        if (text == null || text.isBlank()) {
            return text;
        }
        // None of the voices here performs a delivery tag; read as text, "[laugh]" is the
        // word "laugh" spoken in the middle of the line.
        text = DELIVERY_TAG.matcher(text).replaceAll("").trim();
        String lang = (language == null ? "uz" : language).toLowerCase();
        if (lang.startsWith("uz")) {
            return normalizeUzbek(text);
        }
        if (lang.startsWith("ru")) {
            return normalizeRussian(text);
        }
        if (lang.startsWith("en")) {
            return normalizeEnglish(text);
        }
        return text;
    }

    // ------------------------------------------------------------------
    // Uzbek normalization (Latin & Cyrillic)
    // ------------------------------------------------------------------

    private static String normalizeUzbek(String text) {
        // 1. Percentages (15% -> 15 foiz)
        String out = PERCENTAGE.matcher(text).replaceAll(m -> m.group(1) + " foiz");

        // 2. Currencies
        out = UZ_SUM.matcher(out).replaceAll(m -> cleanNumber(m.group(1)) + " so'm");
        out = USD_AMOUNT.matcher(out).replaceAll(m -> {
            String val = m.group(1) != null ? m.group(1) : m.group(2);
            return cleanNumber(val) + " dollar";
        });

        // 3. ISO dates and dot dates
        out = ISO_DATE.matcher(out).replaceAll(SpeechTextNormalizer::uzbekIsoDate);
        out = DOT_DATE.matcher(out).replaceAll(SpeechTextNormalizer::uzbekDotDate);

        // 4. Phone numbers and clock times, ahead of the reference and quantity rules: both
        // would otherwise claim the digits and read them out as something the caller cannot
        // check against what they said.
        out = PHONE.matcher(out).replaceAll(m -> phone(m.group(), UzbekNumberWords::cardinal));
        out = CLOCK.matcher(out).replaceAll(m -> clock(m, "uz"));

        // 5. Reference numbers
        out = REFERENCE.matcher(out).replaceAll(m -> uzbekReference(m.group()));

        // 6. Ordinals (2026-yil -> ikki ming yigirma oltinchi yil)
        out = UZ_ORDINAL.matcher(out).replaceAll(
                m -> UzbekNumberWords.ordinal(Long.parseLong(m.group(1))) + " " + m.group(2));

        // 7. Quantities
        return QUANTITY.matcher(out).replaceAll(m -> uzbekQuantity(m.group()));
    }

    private static String uzbekIsoDate(MatchResult m) {
        int month = Integer.parseInt(m.group(2));
        if (month < 1 || month > 12) {
            return m.group();
        }
        return UzbekNumberWords.ordinal(Long.parseLong(m.group(1))) + " yil "
                + UzbekNumberWords.ordinal(Long.parseLong(m.group(3))) + " " + UZ_MONTHS[month - 1];
    }

    private static String uzbekDotDate(MatchResult m) {
        int month = Integer.parseInt(m.group(2));
        if (month < 1 || month > 12) {
            return m.group();
        }
        return UzbekNumberWords.ordinal(Long.parseLong(m.group(3))) + " yil "
                + UzbekNumberWords.ordinal(Long.parseLong(m.group(1))) + " " + UZ_MONTHS[month - 1];
    }

    private static String uzbekReference(String token) {
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

    private static String uzbekQuantity(String written) {
        String cleaned = cleanNumber(written);
        if (!cleaned.matches("\\d+")) {
            return written;
        }
        return cleaned.length() > MAX_QUANTITY_DIGITS
                ? UzbekNumberWords.digits(cleaned)
                : UzbekNumberWords.cardinal(Long.parseLong(cleaned));
    }

    // ------------------------------------------------------------------
    // Russian normalization
    // ------------------------------------------------------------------

    private static String normalizeRussian(String text) {
        // 1. Percentages (15% -> 15 процентов)
        String out = PERCENTAGE.matcher(text).replaceAll(m -> m.group(1) + " процентов");

        // 2. Currencies
        out = RU_RUBLE.matcher(out).replaceAll(m -> cleanNumber(m.group(1)) + " рублей");
        out = UZ_SUM.matcher(out).replaceAll(m -> cleanNumber(m.group(1)) + " сум");
        out = USD_AMOUNT.matcher(out).replaceAll(m -> {
            String val = m.group(1) != null ? m.group(1) : m.group(2);
            return cleanNumber(val) + " долларов";
        });

        // 3. Dates
        out = ISO_DATE.matcher(out).replaceAll(SpeechTextNormalizer::russianIsoDate);
        out = DOT_DATE.matcher(out).replaceAll(SpeechTextNormalizer::russianDotDate);

        // 4. Phone numbers and clock times
        out = PHONE.matcher(out).replaceAll(m -> phone(m.group(), RussianNumberWords::cardinal));
        out = CLOCK.matcher(out).replaceAll(m -> clock(m, "ru"));

        // 5. Quantities & Reference numbers
        out = REFERENCE.matcher(out).replaceAll(m -> russianReference(m.group()));
        return QUANTITY.matcher(out).replaceAll(m -> russianQuantity(m.group()));
    }

    private static String russianIsoDate(MatchResult m) {
        int month = Integer.parseInt(m.group(2));
        if (month < 1 || month > 12) {
            return m.group();
        }
        return Long.parseLong(m.group(3)) + " " + RU_MONTHS[month - 1] + " " + m.group(1) + " года";
    }

    private static String russianDotDate(MatchResult m) {
        int month = Integer.parseInt(m.group(2));
        if (month < 1 || month > 12) {
            return m.group();
        }
        return Long.parseLong(m.group(1)) + " " + RU_MONTHS[month - 1] + " " + m.group(3) + " года";
    }

    private static String russianReference(String token) {
        StringBuilder sb = new StringBuilder();
        for (String part : token.split("-")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(part.chars().allMatch(Character::isDigit) ? RussianNumberWords.digits(part) : part);
        }
        return sb.toString();
    }

    private static String russianQuantity(String written) {
        String cleaned = cleanNumber(written);
        if (!cleaned.matches("\\d+")) {
            return written;
        }
        return cleaned.length() > MAX_QUANTITY_DIGITS
                ? RussianNumberWords.digits(cleaned)
                : RussianNumberWords.cardinal(Long.parseLong(cleaned));
    }

    // ------------------------------------------------------------------
    // English normalization
    // ------------------------------------------------------------------

    private static String normalizeEnglish(String text) {
        // 1. Percentages (15% -> 15 percent)
        String out = PERCENTAGE.matcher(text).replaceAll(m -> m.group(1) + " percent");

        // 2. Currencies
        out = USD_AMOUNT.matcher(out).replaceAll(m -> {
            String val = m.group(1) != null ? m.group(1) : m.group(2);
            return cleanNumber(val) + " dollars";
        });

        // 3. Dates
        out = ISO_DATE.matcher(out).replaceAll(SpeechTextNormalizer::englishIsoDate);

        // 4. Phone numbers
        out = PHONE.matcher(out).replaceAll(m -> phone(m.group(), EnglishNumberWords::cardinal));

        // 5. Quantities & Reference numbers
        out = REFERENCE.matcher(out).replaceAll(m -> englishReference(m.group()));
        return QUANTITY.matcher(out).replaceAll(m -> englishQuantity(m.group()));
    }

    private static String englishIsoDate(MatchResult m) {
        int month = Integer.parseInt(m.group(2));
        if (month < 1 || month > 12) {
            return m.group();
        }
        return EN_MONTHS[month - 1] + " " + Long.parseLong(m.group(3)) + ", " + m.group(1);
    }

    private static String englishReference(String token) {
        StringBuilder sb = new StringBuilder();
        for (String part : token.split("-")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(part.chars().allMatch(Character::isDigit) ? EnglishNumberWords.digits(part) : part);
        }
        return sb.toString();
    }

    private static String englishQuantity(String written) {
        String cleaned = cleanNumber(written);
        if (!cleaned.matches("\\d+")) {
            return written;
        }
        return cleaned.length() > MAX_QUANTITY_DIGITS
                ? EnglishNumberWords.digits(cleaned)
                : EnglishNumberWords.cardinal(Long.parseLong(cleaned));
    }

    /**
     * A phone number the way a person reads one back: group by group
     * ("to'qson, bir yuz yigirma uch, qirq besh, oltmish yetti"), never as the single
     * enormous figure the quantity rule would otherwise make of it — a twelve-digit number
     * is inside {@link #MAX_QUANTITY_DIGITS}, so a readback of the caller's own number was
     * coming out as "to'qqiz yuz to'qson sakkiz milliard..." and verified nothing
     * (UNIVERSAL-OPERATOR-PLAN B.7).
     *
     * <p>The comma is the point as much as the words are: it is what the synthesizer pauses
     * on, and the pauses are what let the caller check the number against the one they know.
     */
    private static String phone(String written, LongFunction<String> cardinal) {
        String digits = written.replaceAll("\\D", "");
        int[] groups = digits.length() == 12 ? new int[]{3, 2, 3, 2, 2} : new int[]{2, 3, 2, 2};
        StringBuilder sb = new StringBuilder();
        int at = 0;
        for (int size : groups) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(cardinal.apply(Long.parseLong(digits.substring(at, at + size))));
            at += size;
        }
        return sb.toString();
    }

    /**
     * A clock time in the words a prompt already uses for one ({@link SpokenNumbers#clockTime}),
     * so the hour the agent offers a caller and the hour a queue announcement states are
     * read the same way. English is left out: its form is the digits themselves.
     */
    private static String clock(MatchResult m, String language) {
        return SpokenNumbers.clockTime(
                LocalTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))), language);
    }

    private static String cleanNumber(String written) {
        String cleaned = written.replaceAll("[\\s\\u00A0']", "");
        if (cleaned.matches("\\d{1,3}([.,]\\d{3})+")) {
            cleaned = cleaned.replaceAll("[.,]", "");
        }
        return cleaned;
    }
}
