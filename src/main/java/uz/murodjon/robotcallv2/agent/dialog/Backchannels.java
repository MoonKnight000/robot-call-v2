package uz.murodjon.robotcallv2.agent.dialog;

/**
 * How a caller transcript is cut into words, for the readers that judge it by its words:
 * how much was said over the bot ({@link ClientInputGate}), and whether it was an
 * agreement ({@link Affirmations}).
 *
 * <p>Whether a word or two said over the bot was a listener's "aha" or an answer is no
 * longer decided here from a word list — "ha" is either, depending on the line it was said
 * over, and the model reads it against that line ({@code LlmTurnClient#isTakingTheFloor}).
 */
final class Backchannels {

    private Backchannels() {
    }

    /**
     * Lowercased words, with the apostrophes of {@code xo'p} and {@code o'ldi} dropped
     * rather than treated as breaks — otherwise a single Uzbek word arrives here as two.
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
