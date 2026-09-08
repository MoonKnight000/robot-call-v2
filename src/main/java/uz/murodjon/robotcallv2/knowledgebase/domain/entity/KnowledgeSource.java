package uz.murodjon.robotcallv2.knowledgebase.domain.entity;

import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeSourceStatus;
import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeSourceType;

import java.time.Instant;

/**
 * A document or web page an AI agent answers from. The row is only the handle: what a
 * call actually searches is the {@link KnowledgeChunk}s the indexer extracts from it.
 *
 * @param agentId      null makes the source available to every agent of the company
 * @param storedFileId the uploaded file in {@code stored_file}; null for a {@code URL} source
 * @param chunkCount   how many passages the last successful indexing produced
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
        Instant createdAt
) {
    /** The same source, moved to the front of the indexing queue with its last error cleared. */
    public KnowledgeSource queuedForIndexing() {
        return new KnowledgeSource(id, companyId, agentId, name, sourceType, originalFileName, storedFileId, url,
                KnowledgeSourceStatus.PENDING, null, null, lastIndexedAt, chunkCount, createdAt);
    }

    public KnowledgeSource indexing() {
        return new KnowledgeSource(id, companyId, agentId, name, sourceType, originalFileName, storedFileId, url,
                KnowledgeSourceStatus.PROCESSING, null, null, lastIndexedAt, chunkCount, createdAt);
    }

    public KnowledgeSource indexed(int indexedChunks, Instant at) {
        return new KnowledgeSource(id, companyId, agentId, name, sourceType, originalFileName, storedFileId, url,
                KnowledgeSourceStatus.INDEXED, null, null, at, indexedChunks, createdAt);
    }

    public KnowledgeSource failed(String failureCode, String failureMessage) {
        return new KnowledgeSource(id, companyId, agentId, name, sourceType, originalFileName, storedFileId, url,
                KnowledgeSourceStatus.FAILED, failureCode, failureMessage, lastIndexedAt, 0, createdAt);
    }
}
