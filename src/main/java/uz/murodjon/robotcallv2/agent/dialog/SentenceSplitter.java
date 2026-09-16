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

    /**
     * Shortest piece worth its own synthesis round trip. "Xo'p." or "Tushunarli." alone is
     * under a second of audio, and a line that short cannot cover the round trip of the
     * sentence behind it — on recorded calls the caller heard the acknowledgement, then
     * 400-900 ms of silence, then the actual answer. Kept with the sentence that follows,
     * it costs the first word nothing measurable and the gap disappears.
     */
    static final int MIN_CHUNK_CHARS = 20;

    private SentenceSplitter() {
    }

    /**
     * Cut the next complete sentence off the front of {@code pending}, or return
     * {@code null} if there is not one yet. A sentence shorter than {@link #MIN_CHUNK_CHARS}
     * is not cut on its own but carried into the next one.
     */
    static String takeSentence(StringBuilder pending) {
        for (int i = 0; i < pending.length(); i++) {
            char c = pending.charAt(i);
            // Ignore decimal/number formatting dots and commas: e.g. 1.500.000 or 1,500
            if ((c == '.' || c == ',') && i > 0 && Character.isDigit(pending.charAt(i - 1))
                    && i + 1 < pending.length() && Character.isDigit(pending.charAt(i + 1))) {
                continue;
            }
            if (i + 1 < MIN_CHUNK_CHARS) {
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
            if (c == '!' || c == '?' || c == '\n' || c == '…') {
                String sentence = pending.substring(0, i + 1).trim();
                pending.delete(0, i + 1);
                return sentence.isEmpty() ? null : sentence;
            }
            // Long comma clause split (e.g. >= 30 chars) for early TTS streaming
            if (c == ',' && i >= 30) {
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
