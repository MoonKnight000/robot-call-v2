package uz.murodjon.robotcallv2.knowledgebase.application.dto;

import java.time.Instant;
import java.util.List;

/**
 * How the knowledge base served calls and chats over a period, read from their traces
 * ({@code KNOWLEDGE_RETRIEVED}).
 *
 * @param searches       knowledge searches run for a caller's question
 * @param answered       of those, the ones that found a curated answer or at least one passage
 * @param curatedAnswers of those, answered with a curated answer spoken word for word
 * @param hitRate        {@code answered / searches}, 0 when there were none
 * @param gaps           questions nothing was found for, most asked first
 * @param topSources     the sources most often handed to the model
 * @param topItems       the curated answers most often spoken
 * @param truncated      the period had more searches than were read; the figures cover the newest ones
 */
public record KnowledgeInsightsResponse(
        Instant from,
        Instant to,
        int searches,
        int answered,
        int curatedAnswers,
        double hitRate,
        List<KnowledgeGapRow> gaps,
        List<KnowledgeSourceUsageRow> topSources,
        List<KnowledgeItemUsageRow> topItems,
        boolean truncated
) {
}
