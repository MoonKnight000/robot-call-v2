package uz.murodjon.robotcallv2.agent.tts;

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
    public static String normalize(String text, String language) {
        if (text == null || text.isBlank()) {
            return text;
        }
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

        // 4. Reference numbers
        out = REFERENCE.matcher(out).replaceAll(m -> uzbekReference(m.group()));

        // 5. Ordinals (2026-yil -> ikki ming yigirma oltinchi yil)
        out = UZ_ORDINAL.matcher(out).replaceAll(
                m -> UzbekNumberWords.ordinal(Long.parseLong(m.group(1))) + " " + m.group(2));

        // 6. Quantities
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

        // 4. Quantities & Reference numbers
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

        // 4. Quantities & Reference numbers
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

    private static String cleanNumber(String written) {
        String cleaned = written.replaceAll("[\\s\\u00A0']", "");
        if (cleaned.matches("\\d{1,3}([.,]\\d{3})+")) {
            cleaned = cleaned.replaceAll("[.,]", "");
        }
        return cleaned;
    }
}
