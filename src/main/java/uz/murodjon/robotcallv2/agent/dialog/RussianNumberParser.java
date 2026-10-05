package uz.murodjon.robotcallv2.agent.dialog;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads Russian number words back into numbers — the {@code ru-RU} counterpart of
 * {@link UzbekNumberParser}, for the same reader: {@link FactGuard}.
 *
 * <p>Until this existed a sum spelled out in Russian passed the guard untouched, its one
 * documented gap. A cascade call asks for digits and mostly gets them; on a realtime call
 * the guard reads a transcript of speech, and a Russian caller hears "полтора миллиона"
 * (UNIVERSAL-OPERATOR-PLAN A.3).
 *
 * <p>The rules are the Uzbek reader's: a span is a quantity only once two words have been
 * counted, a count written in digits counts only in front of a scale word, and "ноль" is
 * left out so a reference number read digit by digit is never added to the sum before it.
 * What Russian does differently lives in the tables: hundreds are words of their own
 * ("двести", "пятьсот") rather than a multiplier, a scale word is inflected ("тысяча",
 * "тысячи", "тысяч"), "полтора" is one and a half of the scale after it, and a written
 * count may carry a decimal comma ("1,5 миллиона").
 *
 * <p>One thing is stricter than the Uzbek reader: digits no scale word consumed are left
 * out of the figure, so "полтора миллиона 12 числа" is 1 500 000 and a day, not 1 500 012.
 *
 * <p>Only the forms an agent states a sum in are read; oblique cases ("двухсот",
 * "пятистах") are not. The guard is a net under the prompt, not a grammar.
 */
final class RussianNumberParser {

    /** Words that add to the group being built. */
    private static final Map<String, BigDecimal> COUNTS = new HashMap<>();

    /** Words that close the group by multiplying it, in every inflected form. */
    private static final Map<String, BigDecimal> SCALES = new HashMap<>();

    static {
        String[] ones = {"один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять"};
        for (int i = 0; i < ones.length; i++) {
            COUNTS.put(ones[i], BigDecimal.valueOf(i + 1L));
        }
        COUNTS.put("одна", BigDecimal.ONE);
        COUNTS.put("одно", BigDecimal.ONE);
        COUNTS.put("две", BigDecimal.valueOf(2));
        String[] teens = {"десять", "одиннадцать", "двенадцать", "тринадцать", "четырнадцать",
                "пятнадцать", "шестнадцать", "семнадцать", "восемнадцать", "девятнадцать"};
        for (int i = 0; i < teens.length; i++) {
            COUNTS.put(teens[i], BigDecimal.valueOf(10L + i));
        }
        String[] tens = {"двадцать", "тридцать", "сорок", "пятьдесят", "шестьдесят", "семьдесят",
                "восемьдесят", "девяносто"};
        for (int i = 0; i < tens.length; i++) {
            COUNTS.put(tens[i], BigDecimal.valueOf((i + 2) * 10L));
        }
        String[] hundreds = {"сто", "двести", "триста", "четыреста", "пятьсот", "шестьсот", "семьсот",
                "восемьсот", "девятьсот"};
        for (int i = 0; i < hundreds.length; i++) {
            COUNTS.put(hundreds[i], BigDecimal.valueOf((i + 1) * 100L));
        }
        COUNTS.put("полтора", new BigDecimal("1.5"));
        COUNTS.put("полторы", new BigDecimal("1.5"));
        for (String form : List.of("тысяча", "тысячи", "тысяч", "тысячу")) {
            SCALES.put(form, BigDecimal.valueOf(1_000L));
        }
        for (String form : List.of("миллион", "миллиона", "миллионов")) {
            SCALES.put(form, BigDecimal.valueOf(1_000_000L));
        }
        for (String form : List.of("миллиард", "миллиарда", "миллиардов")) {
            SCALES.put(form, BigDecimal.valueOf(1_000_000_000L));
        }
    }

    /** A count written in digits, decimal comma allowed ("1,5"), or a run of letters. */
    private static final Pattern TOKEN = Pattern.compile("\\d+(?:[.,]\\d+)?|[\\p{L}\\p{M}]+");

    private static final Pattern DIGITS = Pattern.compile("\\d+(?:[.,]\\d+)?");

    /** As in the Uzbek reader: a longer run is a reference number, not a multiplier. */
    private static final int MAX_DIGIT_COUNT = 12;

    private RussianNumberParser() {
    }

    /** Every spelled-out number in {@code text}, as the words that spell it. */
    static List<SpelledNumber> findAll(String text) {
        List<SpelledNumber> found = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return found;
        }
        Matcher m = TOKEN.matcher(text);
        List<String> span = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal current = BigDecimal.ZERO;
        BigDecimal pending = BigDecimal.ZERO;
        int countedTokens = 0;
        int pendingDigits = 0;
        while (m.find()) {
            String token = m.group().toLowerCase(Locale.ROOT).replace('ё', 'е');
            BigDecimal count = COUNTS.get(token);
            BigDecimal scale = SCALES.get(token);
            if (count == null && scale == null) {
                if (token.length() <= MAX_DIGIT_COUNT && DIGITS.matcher(token).matches()) {
                    // "1 миллион 500 тысяч": held until a scale word consumes it, so a phone
                    // number read out never becomes a quantity.
                    pending = pending.add(new BigDecimal(token.replace(',', '.')));
                    span.add(m.group());
                    pendingDigits++;
                    continue;
                }
                flush(found, span, pendingDigits, total.add(current), countedTokens);
                span = new ArrayList<>();
                total = BigDecimal.ZERO;
                current = BigDecimal.ZERO;
                pending = BigDecimal.ZERO;
                countedTokens = 0;
                pendingDigits = 0;
                continue;
            }
            span.add(m.group());
            // Digits followed by a word of the same group belong to it ("2 тысячи пятьсот").
            current = current.add(pending);
            countedTokens += pendingDigits;
            pending = BigDecimal.ZERO;
            pendingDigits = 0;
            if (count != null) {
                current = current.add(count);
                countedTokens++;
                continue;
            }
            total = total.add((current.signum() == 0 ? BigDecimal.ONE : current).multiply(scale));
            current = BigDecimal.ZERO;
            countedTokens++;
        }
        flush(found, span, pendingDigits, total.add(current), countedTokens);
        return found;
    }

    /**
     * A finished span becomes a number only if it counted more than one word. Digits at
     * its tail that no scale word consumed are not part of it.
     */
    private static void flush(List<SpelledNumber> found, List<String> span, int trailingDigits,
                              BigDecimal value, int countedTokens) {
        if (span.isEmpty() || countedTokens < 2 || value.signum() <= 0) {
            return;
        }
        List<String> words = span.subList(0, span.size() - trailingDigits);
        found.add(new SpelledNumber(String.join(" ", words), value));
    }
}
