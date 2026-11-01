package uz.murodjon.robotcallv2.knowledgebase.domain.entity;

import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeSourceStatus;
import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeSourceType;

import java.time.Duration;
import java.time.Instant;

/**
 * A document or web page an AI agent answers from. The row is only the handle: what a
 * call actually searches is the {@link KnowledgeChunk}s the indexer extracts from it — or,
 * for a {@code STRUCTURED_FEED}, the {@link CatalogItem}s.
 *
 * @param agentId        null makes the source available to every agent of the company
 * @param storedFileId   the uploaded file in {@code stored_file}; null for a {@code URL} source
 * @param chunkCount     how many passages (or catalog items) the last successful indexing produced
 * @param feedItemsPath  a feed's JSONPath to its array of items in a JSON answer; null for the whole answer
 * @param feedKeyField   a feed's key column; null for {@code sku}
 &
 * @param collectionId   the named knowledge base the source sits in; null when it is not in one. A source in a
 *                       collection is used only by the agents attached to that collection, so it has no {@code agentId}
 */
public record KnowledgeSource(
        long id,
        long companyId,
        Long agentId,
        String name,
        KnowledgeSourceType sourceType,
        String originalFileName,
        Long storedFileId,
        String url,
        KnowledgeSourceStatus status,
        String errorCode,
        String errorMessage,
        Instant lastIndexedAt,
        int chunkCount,
        Instant createdAt,
        String feedItemsPath,
        String feedKeyField,
        Integer refreshMinutes,
        Long collectionId
) {
    /** The same source, moved to the front of the indexing queue with its last error cleared. */
    public KnowledgeSource queuedForIndexing() {
        return new KnowledgeSource(id, companyId, agentId, name, sourceType, originalFileName, storedFileId, url,
                KnowledgeSourceStatus.PENDING, null, null, lastIndexedAt, chunkCount, createdAt,
                feedItemsPath, feedKeyField, refreshMinutes, collectionId);
    }

    public KnowledgeSource indexing() {
        return new KnowledgeSource(id, companyId, agentId, name, sourceType, originalFileName, storedFileId, url,
                KnowledgeSourceStatus.PROCESSING, null, null, lastIndexedAt, chunkCount, createdAt,
                feedItemsPath, feedKeyField, refreshMinutes, collectionId);
    }

    public KnowledgeSource indexed(int indexedChunks, Instant at) {
        return new KnowledgeSource(id, companyId, agentId, name, sourceType, originalFileName, storedFileId, url,
                KnowledgeSourceStatus.INDEXED, null, null, at, indexedChunks, createdAt,
                feedItemsPath, feedKeyField, refreshMinutes, collectionId);
    }

    public KnowledgeSource failed(String failureCode, String failureMessage) {
        return new KnowledgeSource(id, companyId, agentId, name, sourceType, originalFileName, storedFileId, url,
                KnowledgeSourceStatus.FAILED, failureCode, failureMessage, lastIndexedAt, 0, createdAt,
                feedItemsPath, feedKeyField, refreshMinutes, collectionId);
    }

    /**
     * Whether a URL source (feed, sitemap or page) is due to be fetched again: indexed, set to refresh, and its
     * interval gone since it was last indexed. A failed feed is not — it waits for a retry.
     */
    public boolean isDueForRefresh(Instant now) {
        return (sourceType == KnowledgeSourceType.STRUCTURED_FEED || sourceType == KnowledgeSourceType.SITEMAP
                || sourceType == KnowledgeSourceType.URL)
                && url != null && !url.isBlank()
                && refreshMinutes != null && refreshMinutes > 0
                && status == KnowledgeSourceStatus.INDEXED
                && (lastIndexedAt == null || !lastIndexedAt.plus(Duration.ofMinutes(refreshMinutes)).isAfter(now));
    }
}
