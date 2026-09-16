package uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeSource;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.entity.KnowledgeSourceEntity;

/** Maps between {@link KnowledgeSourceEntity} and {@link KnowledgeSource}. */
@Component
public class KnowledgeSourceEntityMapper {

    public KnowledgeSource toKnowledgeSource(KnowledgeSourceEntity entity) {
        return new KnowledgeSource(
                entity.getId(),
                entity.getCompanyId(),
                entity.getAgentId(),
                entity.getName(),
                entity.getSourceType(),
                entity.getOriginalFileName(),
                entity.getStoredFileId(),
                entity.getUrl(),
                entity.getStatus(),
                entity.getErrorCode(),
                entity.getErrorMessage(),
                entity.getLastIndexedAt(),
                entity.getChunkCount(),
                entity.getCreatedAt(),
                entity.getFeedItemsPath(),
                entity.getFeedKeyField(),
                entity.getRefreshMinutes()
        );
    }

    /** The source's own fields; the company, agent and stored-file references are the adapter's to resolve. */
    public void applyToEntity(KnowledgeSource source, KnowledgeSourceEntity entity) {
        entity.setName(source.name());
        entity.setSourceType(source.sourceType());
        entity.setOriginalFileName(source.originalFileName());
        entity.setChunkCount(source.chunkCount());
        entity.setUrl(source.url());
        entity.setStatus(source.status());
        entity.setErrorCode(source.errorCode());
        entity.setErrorMessage(source.errorMessage());
        entity.setLastIndexedAt(source.lastIndexedAt());
        entity.setFeedItemsPath(source.feedItemsPath());
        entity.setFeedKeyField(source.feedKeyField());
        entity.setRefreshMinutes(source.refreshMinutes());
    }
}
