package uz.murodjon.robotcallv2.knowledgebase.domain.entity;

/**
 * A curated answer chosen for the caller's words — spoken as written, so how it was chosen is kept for the trace.
 *
 * @param answer    the item's answer in the call's language
 * @param score     the cosine of the item to the question, or 0 when the model was not asked
 * @param byKeyword whether one of the item's key or keywords stood in the caller's words as a whole word;
 *                  otherwise it was chosen by meaning alone, above the item threshold
 */
public record KnowledgeItemMatch(
        long itemId,
        String key,
        String answer,
        double score,
        boolean byKeyword
) {
}
