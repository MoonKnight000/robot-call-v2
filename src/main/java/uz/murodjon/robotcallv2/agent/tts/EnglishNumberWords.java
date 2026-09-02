package uz.murodjon.robotcallv2.agent.tts;

/**
 * Writes an integer out in English words for speech synthesis.
 * Package-private helper for {@link SpeechTextNormalizer}.
 */
final class EnglishNumberWords {

    private static final String[] ONES = {
            "zero", "one", "two", "three", "four", "five",
            "six", "seven", "eight", "nine", "ten", "eleven",
            "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
            "seventeen", "eighteen", "nineteen"
    };

    private static final String[] TENS = {
            "", "", "twenty", "thirty", "forty", "fifty",
            "sixty", "seventy", "eighty", "ninety"
    };

    private EnglishNumberWords() {
    }

    /** Cardinal representation of a number in English (e.g. 1500000 -> "one million five hundred thousand"). */
    static String cardinal(long n) {
        if (n < 0) {
            return "minus " + cardinal(-n);
        }
        if (n < 20) {
            return ONES[(int) n];
        }
        if (n < 100) {
            long rest = n % 10;
            return TENS[(int) (n / 10)] + (rest == 0 ? "" : "-" + ONES[(int) rest]);
        }
        if (n < 1_000L) {
            long rest = n % 100L;
            return ONES[(int) (n / 100L)] + " hundred" + (rest == 0 ? "" : " and " + cardinal(rest));
        }
        if (n < 1_000_000L) {
            long rest = n % 1_000L;
            return cardinal(n / 1_000L) + " thousand" + (rest == 0 ? "" : " " + cardinal(rest));
        }
        if (n < 1_000_000_000L) {
            long rest = n % 1_000_000L;
            return cardinal(n / 1_000_000L) + " million" + (rest == 0 ? "" : " " + cardinal(rest));
        }
        long rest = n % 1_000_000_000L;
        return cardinal(n / 1_000_000_000L) + " billion" + (rest == 0 ? "" : " " + cardinal(rest));
    }

    /** Digit by digit readout (e.g. "zero one two..."). */
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
}
