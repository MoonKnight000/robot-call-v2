package uz.murodjon.robotcallv2.knowledgebase.domain.entity;

/**
 * One page a sitemap or URL source read, and how much of the knowledge base came from it.
 *
 * @param chunkCount       its passages
 * @param disabledCount    of those, switched off by a person
 */
public record KnowledgeSourcePage(
        String pageUrl,
        int chunkCount,
        int disabledCount
) {
}
