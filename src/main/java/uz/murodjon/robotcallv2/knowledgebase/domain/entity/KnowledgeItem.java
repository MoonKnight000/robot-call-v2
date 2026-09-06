package uz.murodjon.robotcallv2.knowledgebase.domain.entity;

import java.time.Instant;

/**
 * Domain entity representing an item in the company's Knowledge Base (RAG).
 */
public record KnowledgeItem(
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
    public String answerForLanguage(String language) {
        if (language != null && language.startsWith("ru") && answerRu != null && !answerRu.isBlank()) {
            return answerRu;
        }
        if (language != null && language.startsWith("en") && answerEn != null && !answerEn.isBlank()) {
            return answerEn;
        }
        return answerUz;
    }
}
