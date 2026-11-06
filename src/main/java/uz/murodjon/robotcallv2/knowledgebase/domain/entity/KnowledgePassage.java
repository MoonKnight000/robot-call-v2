package uz.murodjon.robotcallv2.knowledgebase.domain.entity;

/**
 * A chunk that a caller's question matched, with the score it matched at.
 *
 * <p>{@code sourceName} travels with it because the passage ends up in the model's
 * prompt, and a passage with no provenance is one the model can attribute to the wrong
 * document when two of them disagree.
 *
 * @param chunkId the passage's row, so a person reviewing a search can open or switch it off
 * @param pageUrl the page of a sitemap or URL source the passage came from; null for a file
 */
public record KnowledgePassage(
        long chunkId,
        long sourceId,
        String sourceName,
        String content,
        double score,
        String pageUrl
) {
    /** The same passage at another score — the keyword ranking scores candidates the database found. */
    public KnowledgePassage withScore(double newScore) {
        return new KnowledgePassage(chunkId, sourceId, sourceName, content, newScore, pageUrl);
    }
}
