package uz.murodjon.robotcallv2.knowledgebase.domain.entity;

import java.util.Map;

/**
 * A catalog row a search found.
 *
 * @param score from the store, the embedding similarity (0 for a row found by its words
 *              alone); from {@code CatalogRanker}, the share of the caller's words the row carries
 */
public record CatalogMatch(
        long sourceId,
        String sku,
        String content,
        Map<String, String> attributes,
        double score
) {
}
