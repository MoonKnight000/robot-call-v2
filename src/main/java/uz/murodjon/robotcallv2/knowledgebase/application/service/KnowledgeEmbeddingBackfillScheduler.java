package uz.murodjon.robotcallv2.knowledgebase.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.KnowledgeBaseRepository;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.KnowledgeChunkRepository;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.TextEmbedderPort;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeChunk;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItem;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;

import java.util.List;

/**
 * Gives a vector to every passage and curated answer that has none: those indexed while the embedding model
 * was down, those written before passages moved to pgvector (V52), and an item whose save could not embed it.
 * Until then they are found by their words only, so this is what turns a degraded knowledge base back into a
 * full one without anyone pressing retry.
 *
 * <p>A batch a minute, under the cluster lock; nothing happens while there is no model. A text the model
 * refuses keeps its null and is tried again next minute — one batch, not a storm.
 */
@Service
public class KnowledgeEmbeddingBackfillScheduler {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeEmbeddingBackfillScheduler.class);

    /** Passages per minute: a few embedding requests (the adapter batches by 32), so a call's own queries never queue. */
    private static final int CHUNK_BATCH = 256;
    private static final int ITEM_BATCH = 64;

    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final TextEmbedderPort textEmbedder;
    private final ClusterJobLock clusterJobLock;

    public KnowledgeEmbeddingBackfillScheduler(KnowledgeChunkRepository chunkRepository,
                                               KnowledgeBaseRepository knowledgeBaseRepository,
                                               TextEmbedderPort textEmbedder,
                                               ClusterJobLock clusterJobLock) {
        this.chunkRepository = chunkRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.textEmbedder = textEmbedder;
        this.clusterJobLock = clusterJobLock;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void backfill() {
        if (!textEmbedder.isAvailable()) {
            return;
        }
        clusterJobLock.runExclusively("knowledge.embedding-backfill", () -> {
            backfillChunks();
            backfillItems();
        });
    }

    private void backfillChunks() {
        List<KnowledgeChunk> chunks = chunkRepository.findWithoutEmbedding(CHUNK_BATCH);
        if (chunks.isEmpty()) {
            return;
        }
        List<float[]> vectors = textEmbedder.embedAll(chunks.stream().map(KnowledgeChunk::content).toList());
        int filled = 0;
        for (int i = 0; i < chunks.size() && i < vectors.size(); i++) {
            if (vectors.get(i) != null) {
                chunkRepository.updateEmbedding(chunks.get(i).id(), vectors.get(i));
                filled++;
            }
        }
        log.info("Knowledge backfill: embedded {} of {} passages that had no vector", filled, chunks.size());
    }

    private void backfillItems() {
        List<KnowledgeItem> items = knowledgeBaseRepository.findWithoutEmbedding(ITEM_BATCH);
        if (items.isEmpty()) {
            return;
        }
        List<float[]> vectors = textEmbedder.embedAll(items.stream().map(KnowledgeItem::embeddingText).toList());
        int filled = 0;
        for (int i = 0; i < items.size() && i < vectors.size(); i++) {
            if (vectors.get(i) != null) {
                knowledgeBaseRepository.updateEmbedding(items.get(i).id(), vectors.get(i));
                filled++;
            }
        }
        log.info("Knowledge backfill: embedded {} of {} curated answers that had no vector", filled, items.size());
    }
}
