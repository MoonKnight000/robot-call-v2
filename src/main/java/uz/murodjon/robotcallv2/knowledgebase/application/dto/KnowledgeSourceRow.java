package uz.murodjon.robotcallv2.knowledgebase.application.dto;

import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeSourceStatus;
import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeSourceType;

import java.time.Instant;

public record KnowledgeSourceRow(
        long id,
        long companyId,
        Long agentId,
        String agentName,
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
}
