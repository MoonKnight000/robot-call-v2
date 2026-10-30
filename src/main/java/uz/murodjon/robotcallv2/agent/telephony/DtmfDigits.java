package uz.murodjon.robotcallv2.agent.telephony;

/** What the bot may press on a call, whichever engine carries it. */
public final class DtmfDigits {

    /** An IVR selection is a digit or two; a longer string is a model that has lost the plot. */
    private static final int MAX_DIGITS = 12;

    private DtmfDigits() {
    }

    /** Keeps only what a keypad can actually produce ({@code 0-9 * #}), at most {@value #MAX_DIGITS} of it. */
    public static String clean(String digits) {
        if (digits == null) {
            return "";
        }
        StringBuilder kept = new StringBuilder();
        for (char c : digits.toCharArray()) {
            if ((c >= '0' && c <= '9') || c == '*' || c == '#') {
                kept.append(c);
            }
        }
        return kept.length() > MAX_DIGITS ? kept.substring(0, MAX_DIGITS) : kept.toString();
    }
}
