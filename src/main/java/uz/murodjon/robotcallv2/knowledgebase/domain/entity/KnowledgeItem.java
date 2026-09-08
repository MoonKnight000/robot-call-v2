package uz.murodjon.robotcallv2.knowledgebase.domain.entity;

import java.time.Instant;

/**
 * One hand-written answer in the knowledge base — the half a person types, as opposed to
 * the documents and links of {@link KnowledgeSource}.
 */
public record KnowledgeItem(
        long id,
        long companyId,
        /** Agent this answer belongs to; null leaves it available to every agent of the company. */
        Long agentId,
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
