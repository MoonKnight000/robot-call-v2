package uz.murodjon.robotcallv2.knowledgebase.application.port.output;

import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeChunk;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgePassage;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeSourcePage;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.List;
import java.util.Map;

/**
 * The passages a call searches. Every search is narrowed to what one agent may answer from: its own sources,
 * the company-wide ones, the collections attached to it, and {@code extraSourceIds} (a stage's own sources).
 * A null {@code agentId} narrows to the company only. A source that failed has no passages, so every passage
 * of a source that is still indexed, re-indexing or queued is searchable — a refresh does not blank it out.
 * A source switched off and a passage switched off are never searched.
 */
public interface KnowledgeChunkRepository {

    /** Replaces every chunk of the source in one transaction — re-indexing is not additive. */
    void replaceBySourceId(long companyId, long sourceId, List<KnowledgeChunk> chunks);

    void deleteBySourceId(long sourceId);

    /** Deletes the source's passages except those a person edited or switched off. */
    void deleteUncuratedBySourceId(long sourceId);

    /** The vectors the source's passages already have, by content hash, so a re-index embeds only new text. */
    Map<String, float[]> findEmbeddingsBySourceId(long sourceId);

    /** The source's passages a person touched — edited or switched off — with their vectors, for a re-index to keep. */
    List<KnowledgeChunk> findCuratedBySourceId(long sourceId);

    /** The nearest passages by cosine, best first; {@link KnowledgePassage#score()} is the cosine. */
    List<KnowledgePassage> findNearest(long companyId, Long agentId, List<Long> extraSourceIds, float[] query, int limit);

    /**
     * Passages sharing any of the caller's words, best text rank first — candidates only: the caller scores them,
     * and {@link KnowledgePassage#score()} is 0 here.
     */
    List<KnowledgePassage> findByTerms(long companyId, Long agentId, List<Long> extraSourceIds, List<String> terms,
                                       int limit);

    /** Passages with no vector yet — the model was down while indexing, or they predate pgvector — oldest first. */
    List<KnowledgeChunk> findWithoutEmbedding(int limit);

    void updateEmbedding(long chunkId, float[] embedding);

    /**
     * One page of a source's passages in document order, narrowed to those containing {@code search} (case-blind)
     * and to one {@code pageUrl} when given. Vectors are not loaded.
     */
    PageableData<KnowledgeChunk> findPageBySourceId(long sourceId, String search, String pageUrl, int page, int size);

    /** The pages a source's passages came from, in the order they were read; empty for a file. */
    List<KnowledgeSourcePage> findPagesBySourceId(long sourceId);

    /** Null when there is no such passage in that source. Without its vector. */
    KnowledgeChunk findBySourceIdAndId(long sourceId, long chunkId);

    /**
     * Writes a person's change: text, hash, switch and original hash. The vector is written only when the text
     * changed (null then leaves it to the backfill); a passage only switched keeps its vector.
     */
    void updateCuration(KnowledgeChunk chunk);
}
