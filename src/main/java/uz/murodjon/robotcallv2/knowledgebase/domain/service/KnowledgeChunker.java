package uz.murodjon.robotcallv2.knowledgebase.domain.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits an extracted document into the passages that get embedded and searched.
 *
 * <p>Size is a compromise between two failures. Too small and a passage loses the
 * sentence that gave it meaning — "u 30 kungacha davom etadi" retrieved without the
 * paragraph naming the grace period tells the model nothing. Too large and one passage
 * covers several topics, so its vector sits between all of them and matches none well.
 * Around 900 characters is roughly a paragraph of Uzbek prose.
 *
 * <p>Consecutive chunks overlap, because the sentence that answers the question is as
 * likely to fall on a boundary as anywhere else, and a boundary without overlap cuts it
 * in half.
 *
 * <p>Splitting prefers a paragraph break, then a sentence end, and only cuts mid-sentence
 * when a single sentence is longer than the whole budget.
 */
public final class KnowledgeChunker {

    private static final int TARGET_CHARS = 900;
    private static final int OVERLAP_CHARS = 150;
    /** Below this a chunk is not worth a vector — a page number, a stray heading. */
    private static final int MIN_CHARS = 40;

    private KnowledgeChunker() {
    }

    public static List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = normalizeWhitespace(text);
        if (normalized.length() <= TARGET_CHARS) {
            return normalized.length() >= MIN_CHARS ? List.of(normalized) : List.of();
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + TARGET_CHARS, normalized.length());
            if (end < normalized.length()) {
                end = breakBefore(normalized, start, end);
            }
            String chunk = normalized.substring(start, end).trim();
            if (chunk.length() >= MIN_CHARS) {
                chunks.add(chunk);
            }
            if (end >= normalized.length()) {
                break;
            }
            // Step back for the overlap, but never far enough to stand still: without the
            // guard a paragraph break found right at `start` would produce the same chunk
            // for ever.
            start = Math.max(start + 1, end - OVERLAP_CHARS);
        }
        return chunks;
    }

    /**
     * The latest clean break at or before {@code end}, searching back no further than
     * halfway into the chunk — beyond that the break costs more content than it saves.
     */
    private static int breakBefore(String text, int start, int end) {
        int floor = start + TARGET_CHARS / 2;
        int paragraph = text.lastIndexOf("\n\n", end);
        if (paragraph > floor) {
            return paragraph;
        }
        for (int i = end; i > floor; i--) {
            char c = text.charAt(i - 1);
            if ((c == '.' || c == '!' || c == '?' || c == '\n')
                    && (i == text.length() || Character.isWhitespace(text.charAt(i)))) {
                return i;
            }
        }
        return end;
    }

    /**
     * Collapses the runs of spaces and blank lines that every extractor leaves behind,
     * while keeping one blank line as the paragraph mark the splitter looks for.
     */
    private static String normalizeWhitespace(String text) {
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace(' ', ' ')
                .replaceAll("[ \\t]+", " ")
                .replaceAll(" ?\\n ?", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
