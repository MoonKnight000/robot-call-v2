package uz.murodjon.robotcallv2.agent.dialog;

import java.util.Set;

/**
 * Cuts a streamed reply into the pieces that are synthesized one at a time: whole
 * sentences, or a long comma clause, never a fragment too short to cover the round trip
 * of the piece behind it.
 */
final class SentenceSplitter {

    private static final Set<String> ABBREVIATIONS = Set.of(
            // Uzbek
            "mln", "mlrd", "ming", "so'm", "som", "vil", "sh", "t", "prof", "dots", "mas", "kabi",
            // Russian
            "млн", "млрд", "тыс", "руб", "коп", "г", "ул", "д", "кв", "тел", "стр",
            // English
            "mr", "mrs", "ms", "dr", "etc", "vs"
    );

    private SentenceSplitter() {
    }

    /** {@link #takeSentence(StringBuilder, boolean, ChunkerProperties)} for a later piece, at the defaults. */
    static String takeSentence(StringBuilder pending) {
        return takeSentence(pending, false, ChunkerProperties.DEFAULTS);
    }

    /** {@link #takeSentence(StringBuilder, boolean, ChunkerProperties)} at the defaults. */
    static String takeSentence(StringBuilder pending, boolean opening) {
        return takeSentence(pending, opening, ChunkerProperties.DEFAULTS);
    }

    /**
     * Cut the next piece off the front of {@code pending}, or return {@code null} if there is
     * not one yet.
     *
     * <p>A later piece shorter than {@link ChunkerProperties#minChunkChars} is not cut on its
     * own but carried into the next one: "Xo'p." alone is under a second of audio and cannot
     * cover the round trip of the sentence behind it — on recorded calls the caller heard the
     * acknowledgement, then 400-900 ms of silence, then the answer.
     *
     * <p>The opening piece is the one the caller is waiting through, so it is cut at a comma
     * as soon as it has {@link ChunkerProperties#openingClauseWords} words.
     */
    static String takeSentence(StringBuilder pending, boolean opening, ChunkerProperties limits) {
        int minChars = opening ? limits.openingMinChars() : limits.minChunkChars();
        int longClauseChars = limits.longClauseChars();
        int words = 0;
        boolean inWord = false;
        for (int i = 0; i < pending.length(); i++) {
            char c = pending.charAt(i);
            if (Character.isWhitespace(c)) {
                inWord = false;
            } else if (!inWord) {
                inWord = true;
                words++;
            }
            // Ignore decimal/number formatting dots and commas: e.g. 1.500.000 or 1,500
            if ((c == '.' || c == ',') && i > 0 && Character.isDigit(pending.charAt(i - 1))
                    && i + 1 < pending.length() && Character.isDigit(pending.charAt(i + 1))) {
                continue;
            }
            if (i + 1 < minChars) {
                continue; // too short to be worth a round trip of its own
            }
            if (c == '.') {
                if (!isDotSentenceEnd(pending, i)) {
                    continue;
                }
                String sentence = pending.substring(0, i + 1).trim();
                pending.delete(0, i + 1);
                return sentence.isEmpty() ? null : sentence;
            }
            if (c == '!' || c == '?' || c == '\n' || c == '…' || c == ';') {
                String sentence = pending.substring(0, i + 1).trim();
                pending.delete(0, i + 1);
                return sentence.isEmpty() ? null : sentence;
            }
            // The opening piece is cut at a comma once it has a few words: the caller is waiting
            // through it. The next character must be there and be a space — a stream may still
            // be about to continue "1," with "500".
            if (opening && c == ',' && words >= limits.openingClauseWords()) {
                if (i + 1 >= pending.length() || !Character.isWhitespace(pending.charAt(i + 1))) {
                    continue;
                }
                String clause = pending.substring(0, i + 1).trim();
                pending.delete(0, i + 1);
                return clause.isEmpty() ? null : clause;
            }
            // A later piece is cut at a comma only when the clause is really long. Each piece is
            // synthesized on its own and ends on a sentence-final fall, so a 30-char cut made
            // every "Murodjon aka," sound like the end of a sentence — the robotic cadence.
            if (c == ',' && i >= longClauseChars) {
                if (i + 1 < pending.length() && !Character.isWhitespace(pending.charAt(i + 1))) {
                    continue;
                }
                String sentence = pending.substring(0, i + 1).trim();
                pending.delete(0, i + 1);
                return sentence.isEmpty() ? null : sentence;
            }
        }
        return null;
    }

    private static boolean isDotSentenceEnd(StringBuilder sb, int dotIndex) {
        int start = dotIndex - 1;
        while (start >= 0 && (Character.isLetterOrDigit(sb.charAt(start)) || sb.charAt(start) == '\'' || sb.charAt(start) == '‘' || sb.charAt(start) == '’')) {
            start--;
        }
        String token = sb.substring(start + 1, dotIndex).toLowerCase();
        if (ABBREVIATIONS.contains(token)) {
            return false;
        }
        if (dotIndex + 1 < sb.length()) {
            char next = sb.charAt(dotIndex + 1);
            if (Character.isLetterOrDigit(next)) {
                return false;
            }
            int nextNonWs = dotIndex + 1;
            while (nextNonWs < sb.length() && Character.isWhitespace(sb.charAt(nextNonWs))) {
                nextNonWs++;
            }
            if (nextNonWs < sb.length() && Character.isLowerCase(sb.charAt(nextNonWs))) {
                return false;
            }
        }
        return true;
    }
}
