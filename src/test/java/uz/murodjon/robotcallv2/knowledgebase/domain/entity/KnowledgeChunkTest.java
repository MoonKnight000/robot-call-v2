package uz.murodjon.robotcallv2.knowledgebase.domain.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A person's correction must outlive the next refresh of the page it came from. */
class KnowledgeChunkTest {

    private static final float[] VECTOR = {1f, 0f};

    @Test
    void anEditKeepsTheHashOfTheTextTheIndexerProducedAcrossSecondEdits() {
        KnowledgeChunk indexed = new KnowledgeChunk(5L, 1L, 0, "Narx 100", "h-original", null, VECTOR, false, null);

        KnowledgeChunk once = indexed.edited("Narx 120", "h-120", null);
        KnowledgeChunk twice = once.edited("Narx 130", "h-130", null);

        assertThat(once.originalHash()).isEqualTo("h-original");
        assertThat(twice.originalHash()).isEqualTo("h-original");
        assertThat(twice.content()).isEqualTo("Narx 130");
    }

    @Test
    void aRefreshThatReadsTheSameTextCarriesTheEditAndTheSwitch() {
        KnowledgeChunk previous = new KnowledgeChunk(5L, 1L, 3, "Narx 130", "h-130", "https://a.uz/p", VECTOR, true,
                "h-original");
        KnowledgeChunk fresh = KnowledgeChunk.toIndex(1L, 7, "Narx 100", "h-original", "https://a.uz/p", null);

        KnowledgeChunk carried = fresh.carrying(previous);

        assertThat(carried.content()).isEqualTo("Narx 130");
        assertThat(carried.contentHash()).isEqualTo("h-130");
        assertThat(carried.embedding()).isSameAs(VECTOR);
        assertThat(carried.disabled()).isTrue();
        assertThat(carried.originalHash()).isEqualTo("h-original");
        assertThat(carried.ordinal()).isEqualTo(7);
    }

    @Test
    void aSwitchedOffPassageThatWasNotEditedCarriesOnlyTheSwitch() {
        KnowledgeChunk previous = new KnowledgeChunk(5L, 1L, 0, "Eski matn", "h", null, VECTOR, true, null);
        KnowledgeChunk fresh = KnowledgeChunk.toIndex(1L, 0, "Eski matn", "h", null, null);

        KnowledgeChunk carried = fresh.carrying(previous);

        assertThat(carried.disabled()).isTrue();
        assertThat(carried.originalHash()).isNull();
        assertThat(carried.embedding()).isNull();
    }
}
