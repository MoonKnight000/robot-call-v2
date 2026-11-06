package uz.murodjon.robotcallv2.knowledgebase.application.dto;

/**
 * How often one curated answer was spoken.
 *
 * @param byMeaning of {@code hits}, those chosen by meaning rather than by a keyword — a high share says the item
 *                  needs more keywords, or that the threshold is letting near misses through
 */
public record KnowledgeItemUsageRow(
        long itemId,
        int hits,
        int byMeaning
) {
}
