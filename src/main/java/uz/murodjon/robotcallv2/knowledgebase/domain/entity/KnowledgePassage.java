package uz.murodjon.robotcallv2.knowledgebase.domain.entity;

/**
 * A chunk that a caller's question matched, with the score it matched at.
 *
 * <p>{@code sourceName} travels with it because the passage ends up in the model's
 * prompt, and a passage with no provenance is one the model can attribute to the wrong
 * document when two of them disagree.
 */
public record KnowledgePassage(
        long sourceId,
        String sourceName,
        String content,
        double score
) {
}
