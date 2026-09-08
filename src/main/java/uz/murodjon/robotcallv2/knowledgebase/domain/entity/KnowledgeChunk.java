package uz.murodjon.robotcallv2.knowledgebase.domain.entity;

/**
 * One searchable passage of a {@link KnowledgeSource} — a few hundred characters of the
 * document, in the order it appeared.
 *
 * @param embedding the passage's vector, or {@code null} when the embedding model could
 *                  not be reached while indexing. Such a chunk is still stored and still
 *                  reachable through the keyword fallback; a retry fills the vector in.
 */
public record KnowledgeChunk(
        long id,
        long sourceId,
        int ordinal,
        String content,
        float[] embedding
) {
    public static KnowledgeChunk toIndex(long sourceId, int ordinal, String content, float[] embedding) {
        return new KnowledgeChunk(0L, sourceId, ordinal, content, embedding);
    }
}
