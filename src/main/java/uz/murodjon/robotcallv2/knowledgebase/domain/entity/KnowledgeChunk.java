package uz.murodjon.robotcallv2.knowledgebase.domain.entity;

/**
 * One searchable passage of a {@link KnowledgeSource} — a few hundred characters of the
 * document, in the order it appeared.
 *
 * @param contentHash  SHA-256 of {@code content}: a re-index embeds again only the passages whose text changed
 * @param pageUrl      the page the passage came from — a sitemap's page, a URL source's own address; null for a file
 * @param embedding    the passage's vector, or {@code null} when the embedding model could
 *                     not be reached while indexing. Such a chunk is still stored and still
 *                     reachable through the keyword search; the backfill fills the vector in.
 * @param disabled     switched off by a person: stored, never searched
 * @param originalHash the hash of the text the indexer produced, when a person edited the passage; null when
 *                     it is as indexed. A re-index that reads that text again keeps the edit
 */
public record KnowledgeChunk(
        long id,
        long sourceId,
        int ordinal,
        String content,
        String contentHash,
        String pageUrl,
        float[] embedding,
        boolean disabled,
        String originalHash
) {
    public static KnowledgeChunk toIndex(long sourceId, int ordinal, String content, String contentHash,
                                         String pageUrl, float[] embedding) {
        return new KnowledgeChunk(0L, sourceId, ordinal, content, contentHash, pageUrl, embedding, false, null);
    }

    public KnowledgeChunk withEmbedding(float[] vector) {
        return new KnowledgeChunk(id, sourceId, ordinal, content, contentHash, pageUrl, vector, disabled, originalHash);
    }

    public KnowledgeChunk withDisabled(boolean off) {
        return new KnowledgeChunk(id, sourceId, ordinal, content, contentHash, pageUrl, embedding, off, originalHash);
    }

    /**
     * The passage with a person's text in place of the indexed one. The original hash is kept from the first
     * edit, so a second edit still matches what the indexer will read; the vector is the caller's to supply.
     */
    public KnowledgeChunk edited(String newContent, String newHash, float[] vector) {
        return new KnowledgeChunk(id, sourceId, ordinal, newContent, newHash, pageUrl, vector, disabled,
                originalHash != null ? originalHash : contentHash);
    }

    /**
     * This freshly indexed passage carrying what a person did to the same text before: their edit (text,
     * vector, switch) when {@code previous} was edited, otherwise only the switch.
     */
    public KnowledgeChunk carrying(KnowledgeChunk previous) {
        if (previous.originalHash() != null) {
            return new KnowledgeChunk(id, sourceId, ordinal, previous.content(), previous.contentHash(), pageUrl,
                    previous.embedding(), previous.disabled(), previous.originalHash());
        }
        return withDisabled(previous.disabled());
    }
}
