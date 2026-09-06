package uz.murodjon.robotcallv2.knowledgebase.presentation.dto;

import java.time.Instant;

public record KnowledgeItemResponse(
        long id,
        long companyId,
        String key,
        String topic,
        String title,
        String answerUz,
        String answerRu,
        String answerEn,
        String keywords,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
