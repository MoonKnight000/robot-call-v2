package uz.murodjon.robotcallv2.knowledgebase.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import uz.murodjon.robotcallv2.knowledgebase.application.port.output.KnowledgeChunkRepository;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.TextEmbedderPort;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.IndexedChunk;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeChunk;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgePassage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeRetrievalServiceTest {

    private static final float[] PAYMENT = {1.0f, 0.0f, 0.0f};
    private static final float[] LEGAL = {0.0f, 1.0f, 0.0f};

    private StubChunkRepository chunkRepository;

    @BeforeEach
    void setUp() {
        chunkRepository = new StubChunkRepository(List.of(
                new IndexedChunk(1L, null, "To'lov qoidalari", "To'lovni Click yoki Payme orqali qiling.", PAYMENT),
                new IndexedChunk(2L, 7L, "Sud amaliyoti", "Qarz uzoq to'lanmasa ish sudga oshadi.", LEGAL)
        ));
    }

    @Test
    void returnsThePassageClosestToTheQuestion() {
        KnowledgeRetrievalService service = new KnowledgeRetrievalService(chunkRepository, embedder(PAYMENT));

        List<KnowledgePassage> hits = service.findRelevantPassages(1L, 7L, "Qanday to'lasam bo'ladi?", 3);

        assertThat(hits).hasSize(1);
        assertThat(hits.getFirst().sourceName()).isEqualTo("To'lov qoidalari");
        assertThat(hits.getFirst().score()).isGreaterThan(0.9);
    }

    @Test
    void aPassageThatIsMerelyTheLeastUnrelatedIsNotReturned() {
        // Orthogonal to both documents: cosine 0, far below the floor.
        KnowledgeRetrievalService service = new KnowledgeRetrievalService(
                chunkRepository, embedder(new float[]{0.0f, 0.0f, 1.0f}));

        assertThat(service.findRelevantPassages(1L, 7L, "Bugun havo qanday?", 3)).isEmpty();
    }

    @Test
    void anotherAgentsSourceIsNeverReturned() {
        KnowledgeRetrievalService service = new KnowledgeRetrievalService(chunkRepository, embedder(LEGAL));

        // Source 2 belongs to agent 7; agent 9 must not see it, and the company-wide
        // source 1 does not match this question.
        assertThat(service.findRelevantPassages(1L, 9L, "Sudga berishadimi?", 3)).isEmpty();
        assertThat(service.findRelevantPassages(1L, 7L, "Sudga berishadimi?", 3))
                .extracting(KnowledgePassage::sourceId)
                .containsExactly(2L);
    }

    @Test
    void withNoEmbeddingModelItFallsBackToKeywords() {
        KnowledgeRetrievalService service = new KnowledgeRetrievalService(chunkRepository, embedder(null));

        List<KnowledgePassage> hits = service.findRelevantPassages(1L, 7L, "Click orqali to'lash", 3);

        assertThat(hits).extracting(KnowledgePassage::sourceId).containsExactly(1L);
    }

    @Test
    void anUtteranceTooShortToBeAQuestionIsNotSearched() {
        StubEmbedder embedder = embedder(PAYMENT);
        KnowledgeRetrievalService service = new KnowledgeRetrievalService(chunkRepository, embedder);

        assertThat(service.findRelevantPassages(1L, 7L, "ha", 3)).isEmpty();
        assertThat(embedder.queries.get()).isZero();
    }

    @Test
    void passagesAreReadOncePerCompanyAndAgainAfterEviction() {
        KnowledgeRetrievalService service = new KnowledgeRetrievalService(chunkRepository, embedder(PAYMENT));

        service.findRelevantPassages(1L, 7L, "Qanday to'layman?", 3);
        service.findRelevantPassages(1L, 7L, "Qayerga to'layman?", 3);
        assertThat(chunkRepository.reads.get()).isEqualTo(1);

        service.evictCompany(1L);
        service.findRelevantPassages(1L, 7L, "Qanday to'layman?", 3);
        assertThat(chunkRepository.reads.get()).isEqualTo(2);
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
        public boolean available() {
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

    private static final class StubChunkRepository implements KnowledgeChunkRepository {
        private final List<IndexedChunk> chunks;
        private final AtomicInteger reads = new AtomicInteger();

        private StubChunkRepository(List<IndexedChunk> chunks) {
            this.chunks = chunks;
        }

        @Override
        public void replaceBySourceId(long sourceId, List<KnowledgeChunk> replacement) {
        }

        @Override
        public void deleteBySourceId(long sourceId) {
        }

        @Override
        public List<IndexedChunk> findIndexedByCompanyId(long companyId) {
            reads.incrementAndGet();
            return chunks;
        }
    }
}
