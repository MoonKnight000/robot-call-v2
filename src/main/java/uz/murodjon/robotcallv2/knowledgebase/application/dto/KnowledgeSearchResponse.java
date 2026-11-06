package uz.murodjon.robotcallv2.knowledgebase.application.dto;

import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItemMatch;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgePassage;

import java.util.List;

/**
 * What a call would have got for the question.
 *
 * @param curated  the curated answer a call would speak word for word, or null; when present a call does not
 *                 search the passages, but they are listed here anyway so both can be compared
 * @param passages what the model would be handed, best first
 * @param limit    the top-k the search ran with
 * @param minScore the similarity floor it ran with
 */
public record KnowledgeSearchResponse(
        KnowledgeItemMatch curated,
        List<KnowledgePassage> passages,
        int limit,
        double minScore,
        long durationMs
) {
}
