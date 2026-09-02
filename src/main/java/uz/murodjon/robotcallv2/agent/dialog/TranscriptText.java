package uz.murodjon.robotcallv2.agent.dialog;

/**
 * Comparing two pieces of recognized speech for what they say rather than how they are
 * written. Used at both ends of the pipeline: {@link ClientInputGate} asks whether a
 * caller transcript is the bot's own line coming back, and {@link TurnRunner} asks
 * whether a final says what the interim it started guessing on said.
 */
final class TranscriptText {

    private TranscriptText() {
    }

    /**
     * Whether an interim and a final are the same utterance. Compared on letters and
     * digits alone — a recognizer settling on its final routinely changes the casing and
     * the punctuation of text it is otherwise no longer revising.
     */
    static boolean saysTheSame(String interim, String finalText) {
        return normalize(interim).equals(normalize(finalText));
    }

    /** Letters and digits only, lowercased, single-spaced — so punctuation cannot hide a match. */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder normalized = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                normalized.append(c);
            } else if (!normalized.isEmpty() && normalized.charAt(normalized.length() - 1) != ' ') {
                normalized.append(' ');
            }
        }
        return normalized.toString().trim();
    }
}
