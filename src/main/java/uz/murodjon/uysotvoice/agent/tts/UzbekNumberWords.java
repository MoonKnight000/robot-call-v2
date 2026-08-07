package uz.murodjon.uysotvoice.agent.tts;

/**
 * Writes an integer out in Uzbek words, the way a person reads one aloud.
 *
 * <p>Uzbek numerals are fully regular, so this is a plain positional expansion: the two
 * places it deviates are {@code 100} and {@code 1000}, which are spoken as "yuz" and
 * "ming" rather than "bir yuz"/"bir ming", while a million upwards keeps its "bir".
 *
 * <p>Package-private on purpose — {@link SpeechTextNormalizer} is the only thing that
 * should decide when a digit run becomes speech.
 */
final class UzbekNumberWords {

    private static final String[] ONES = {
            "nol", "bir", "ikki", "uch", "to'rt", "besh", "olti", "yetti", "sakkiz", "to'qqiz"
    };

    private static final String[] TENS = {
            "", "o'n", "yigirma", "o'ttiz", "qirq", "ellik", "oltmish", "yetmish", "sakson", "to'qson"
    };

    /** Which final letter takes {@code -nchi} instead of {@code -inchi} (see {@link #ordinal}). */
    private static final String VOWELS = "aeiou";

    private UzbekNumberWords() {
    }

    /** {@code 1500000} → "bir million besh yuz ming". */
    static String cardinal(long n) {
        if (n < 0) {
            return "minus " + cardinal(-n);
        }
        if (n < 10) {
            return ONES[(int) n];
        }
        if (n < 100) {
            long rest = n % 10;
            return TENS[(int) (n / 10)] + (rest == 0 ? "" : " " + ONES[(int) rest]);
        }
        if (n < 1_000L) {
            return group(n, 100L, "yuz");
        }
        if (n < 1_000_000L) {
            return group(n, 1_000L, "ming");
        }
        if (n < 1_000_000_000L) {
            return group(n, 1_000_000L, "million");
        }
        return group(n, 1_000_000_000L, "milliard");
    }

    /**
     * {@code 2026} → "ikki ming yigirma oltinchi". Only the last word takes the suffix,
     * which is what makes a year or a day-of-month sound like a date rather than a count.
     */
    static String ordinal(long n) {
        String words = cardinal(n);
        char last = words.charAt(words.length() - 1);
        return words + (VOWELS.indexOf(last) >= 0 ? "nchi" : "inchi");
    }

    /**
     * Reads a digit run one digit at a time — {@code "00123"} → "nol nol bir ikki uch".
     *
     * <p>How a reference number is read out: it is a label, not a quantity, and its
     * leading zeros are part of it. Non-digits are skipped.
     */
    static String digits(String run) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < run.length(); i++) {
            char c = run.charAt(i);
            if (c >= '0' && c <= '9') {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(ONES[c - '0']);
            }
        }
        return sb.toString();
    }

    /** One scale step: the count, its scale word, then whatever is left below it. */
    private static String group(long n, long scale, String name) {
        long count = n / scale;
        long rest = n % scale;
        // "yuz"/"ming" stand alone at one; "bir million" does not.
        String head = (count == 1 && scale <= 1_000L) ? name : cardinal(count) + " " + name;
        return rest == 0 ? head : head + " " + cardinal(rest);
    }
}
