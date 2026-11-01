package uz.murodjon.robotcallv2.knowledgebase.domain.entity;

/**
 * What a knowledge search is asked, split by what each ranking needs.
 *
 * @param question         the caller's latest words; keyword matching runs on this alone, and a
 *                         question too short to mean anything is not searched even when context follows
 * @param semanticText     what is embedded: the question plus the conversation around it, so a bare
 *                         "narxi?" still lands on the right product. Null means the question itself
 * @param minSemanticScore the cosine floor for this search; null means the service default
 */
public record KnowledgeQuery(String question, String semanticText, Double minSemanticScore) {

    /** A plain question: no context, default threshold. */
    public static KnowledgeQuery of(String question) {
        return new KnowledgeQuery(question, null, null);
    }
}
