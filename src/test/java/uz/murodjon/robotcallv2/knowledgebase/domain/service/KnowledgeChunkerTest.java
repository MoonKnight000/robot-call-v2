package uz.murodjon.robotcallv2.knowledgebase.domain.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeChunkerTest {

    @Test
    void emptyInputProducesNoChunks() {
        assertThat(KnowledgeChunker.chunk(null)).isEmpty();
        assertThat(KnowledgeChunker.chunk("   \n\n  ")).isEmpty();
    }

    @Test
    void shortDocumentStaysOneChunk() {
        String text = "Imtiyozli davr shartnomada belgilanadi va odatda 30 kungacha davom etadi.";
        assertThat(KnowledgeChunker.chunk(text)).containsExactly(text);
    }

    @Test
    void aFragmentTooShortToBeWorthAVectorIsDropped() {
        assertThat(KnowledgeChunker.chunk("12")).isEmpty();
    }

    @Test
    void longDocumentIsSplitAndEveryChunkStaysNearTheBudget() {
        List<String> chunks = KnowledgeChunker.chunk(sentences(400));

        assertThat(chunks).hasSizeGreaterThan(1);
        // The budget is 900; a chunk may run a little under after a sentence break, but
        // never over, or the passage stops being a paragraph and starts being a page.
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(900));
    }

    @Test
    void consecutiveChunksOverlapSoASentenceOnABoundaryIsNotLost() {
        List<String> chunks = KnowledgeChunker.chunk(sentences(400));

        // The overlap is 150 characters, so the tail of one chunk has to reappear in the
        // next — that is the whole point of it: the answering sentence is as likely to sit
        // on a boundary as anywhere else.
        String first = chunks.get(0);
        String tail = first.substring(first.length() - 50).trim();
        assertThat(chunks.get(1)).contains(tail);
    }

    @Test
    void splittingTerminatesOnTextWithNoSentenceBreaks() {
        // One run of characters with nothing to break on: the splitter must still advance
        // rather than cutting the same chunk for ever.
        String wall = "a".repeat(5000);

        List<String> chunks = KnowledgeChunker.chunk(wall);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.size()).isLessThan(20);
    }

    @Test
    void paragraphBreaksSurviveNormalizationSoTheSplitterCanUseThem() {
        String text = "Imtiyozli davr shartnomada belgilanadi.\r\n\r\n\r\n\r\n"
                + "To'lovni   bank   kassasida amalga oshirish mumkin.";

        List<String> chunks = KnowledgeChunker.chunk(text);

        // CRLF becomes LF, runs of spaces collapse, and a run of blank lines collapses to
        // the single blank line the splitter looks for — but never to none.
        assertThat(chunks).containsExactly(
                "Imtiyozli davr shartnomada belgilanadi.\n\nTo'lovni bank kassasida amalga oshirish mumkin.");
    }

    /** {@code count} short Uzbek sentences — long enough to force several chunks. */
    private static String sentences(int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            text.append("To'lov ").append(i).append("-bandda bank kassasida amalga oshiriladi. ");
        }
        return text.toString();
    }
}
