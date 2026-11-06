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
    /**
     * What the item is embedded as: its title, key and keywords say what it is about, the answers in every
     * language it has what it says — so a caller's question in Russian still lands on an item written in Uzbek.
     */
    public String embeddingText() {
        StringBuilder text = new StringBuilder();
        for (String part : new String[]{title, key, keywords, answerUz, answerRu, answerEn}) {
            if (part != null && !part.isBlank()) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(part.trim());
            }
        }
        return text.toString();
    }

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
