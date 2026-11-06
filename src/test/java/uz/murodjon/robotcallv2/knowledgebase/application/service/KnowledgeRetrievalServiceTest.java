package uz.murodjon.robotcallv2.knowledgebase.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.KnowledgeBaseRepository;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.KnowledgeChunkRepository;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.TextEmbedderPort;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItem;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItemMatch;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgePassage;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.config.KnowledgeBaseProperties;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The database does the nearest-neighbour and text search (pgvector); what is pinned here is what the service
 * does with the candidates: the similarity floor, the keyword score, the fusion, and the curated-answer choice.
 */
class KnowledgeRetrievalServiceTest {

    private static final float[] VECTOR = {1.0f, 0.0f, 0.0f};
    private static final KnowledgeBaseProperties PROPERTIES = new KnowledgeBaseProperties(800, 3, 5, 0.80, 100);

    private KnowledgeChunkRepository chunkRepository;
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @BeforeEach
    void setUp() {
        chunkRepository = mock(KnowledgeChunkRepository.class);
        knowledgeBaseRepository = mock(KnowledgeBaseRepository.class);
        when(chunkRepository.findByTerms(anyLong(), any(), anyList(), anyList(), anyInt())).thenReturn(List.of());
        when(chunkRepository.findNearest(anyLong(), any(), anyList(), any(), anyInt())).thenReturn(List.of());
    }

    @Test
    void passagesBelowTheSimilarityFloorAreDropped() {
        when(chunkRepository.findNearest(anyLong(), any(), anyList(), any(), anyInt())).thenReturn(List.of(
                passage(1L, "To'lovni Click yoki Payme orqali qiling.", 0.91),
                passage(2L, "Ofis soat 9 da ochiladi.", 0.40)));

        List<KnowledgePassage> hits = service(embedder(VECTOR)).findRelevantPassages(1L, 7L, "Qanday to'lasam bo'ladi?", 3);

        assertThat(hits).extracting(KnowledgePassage::sourceId).containsExactly(1L);
    }

    @Test
    void withNoEmbeddingModelItFallsBackToKeywordCandidatesScoredByCoverage() {
        when(chunkRepository.findByTerms(anyLong(), any(), anyList(), anyList(), anyInt())).thenReturn(List.of(
                passage(1L, "To'lovni Click orqali qiling.", 0.0),
                passage(2L, "Bu matnda so'zlar yo'q.", 0.0)));

        List<KnowledgePassage> hits = service(embedder(null)).findRelevantPassages(1L, 7L, "Click orqali to'lash", 3);

        assertThat(hits).extracting(KnowledgePassage::sourceId).containsExactly(1L);
        assertThat(hits.getFirst().score()).isGreaterThan(0.5);
        verify(chunkRepository, never()).findNearest(anyLong(), any(), anyList(), any(), anyInt());
    }

    @Test
    void aPassageBothRankingsFoundComesFirst() {
        when(chunkRepository.findNearest(anyLong(), any(), anyList(), any(), anyInt())).thenReturn(List.of(
                passage(2L, "Shartnoma muddati bir yil.", 0.80),
                passage(1L, "Shartnoma raqami 12345 bo'yicha to'lov.", 0.70)));
        when(chunkRepository.findByTerms(anyLong(), any(), anyList(), anyList(), anyInt())).thenReturn(List.of(
                passage(1L, "Shartnoma raqami 12345 bo'yicha to'lov.", 0.0)));

        List<KnowledgePassage> hits = service(embedder(VECTOR)).findRelevantPassages(1L, 7L, "shartnoma 12345 qarzi", 2);

        assertThat(hits).extracting(KnowledgePassage::sourceId).containsExactly(1L, 2L);
    }

    @Test
    void anUtteranceTooShortToBeAQuestionIsNotSearched() {
        StubEmbedder embedder = embedder(VECTOR);

        assertThat(service(embedder).findRelevantPassages(1L, 7L, "ha", 3)).isEmpty();
        assertThat(embedder.queries.get()).isZero();
        verifyNoInteractions(chunkRepository);
    }

    @Test
    void theSameWordsAreEmbeddedOnce() {
        StubEmbedder embedder = embedder(VECTOR);
        KnowledgeRetrievalService service = service(embedder);

        service.findRelevantPassages(1L, 7L, "Qanday to'layman?", 3);
        service.findRelevantPassages(1L, 7L, "Qanday to'layman?", 3);

        assertThat(embedder.queries.get()).isEqualTo(1);
    }

    @Test
    void aCompanyWithNoCuratedItemsCostsNoEmbedding() {
        StubEmbedder embedder = embedder(VECTOR);
        when(knowledgeBaseRepository.findAllActiveByCompanyIdAndAgentId(1L, 7L)).thenReturn(List.of());

        assertThat(service(embedder).findCuratedAnswer(1L, 7L, "To'lov usullari qanaqa?", "uz-UZ")).isNull();
        assertThat(embedder.queries.get()).isZero();
    }

    @Test
    void aCuratedItemCloseInMeaningIsChosenWithoutItsKeywords() {
        KnowledgeItem item = item(5L, "payment-methods", "click, payme", "Click yoki Payme orqali to'lashingiz mumkin.");
        when(knowledgeBaseRepository.findAllActiveByCompanyIdAndAgentId(1L, 7L)).thenReturn(List.of(item));
        when(knowledgeBaseRepository.findSimilarities(eq(1L), eq(7L), any(), anyInt())).thenReturn(Map.of(5L, 0.86));

        KnowledgeItemMatch match = service(embedder(VECTOR)).findCuratedAnswer(1L, 7L, "Pulni qanday o'tkazsam bo'ladi?", "uz-UZ");

        assertThat(match).isNotNull();
        assertThat(match.itemId()).isEqualTo(5L);
        assertThat(match.byKeyword()).isFalse();
    }

    private KnowledgeRetrievalService service(TextEmbedderPort embedder) {
        return new KnowledgeRetrievalService(chunkRepository, knowledgeBaseRepository, embedder, PROPERTIES);
    }

    private static KnowledgePassage passage(long sourceId, String content, double score) {
        return new KnowledgePassage(sourceId * 100, sourceId, "Manba " + sourceId, content, score, null);
    }

    private static KnowledgeItem item(long id, String key, String keywords, String answer) {
        return new KnowledgeItem(id, 1L, null, key, "payment", "To'lov usullari", answer, null, null, keywords,
                true, Instant.now(), Instant.now());
    }

    private static StubEmbedder embedder(float[] queryVector) {
        return new StubEmbedder(queryVector);
    }

    private static final class StubEmbedder implements TextEmbedderPort {
        private final float[] queryVector;
        private final AtomicInteger queries = new AtomicInteger();

        private StubEmbedder(float[] queryVector) {
            this.queryVector = queryVector;
        }

        @Override
        public boolean isAvailable() {
            return queryVector != null;
        }

        @Override
        public List<float[]> embedAll(List<String> texts) {
            return new ArrayList<>(texts.stream().map(t -> queryVector).toList());
        }

        @Override
        public float[] embedQuery(String text) {
            queries.incrementAndGet();
            return queryVector;
        }
    }
}
