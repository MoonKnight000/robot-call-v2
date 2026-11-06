package uz.murodjon.robotcallv2.knowledgebase.application.dto;

/**
 * One passage of a source as the source's page lists it.
 *
 * @param pageUrl  the page it came from; null for a file
 * @param disabled switched off: kept, never searched
 * @param edited   a person changed its text; a refresh that reads the same original text keeps the change
 */
public record KnowledgeChunkRow(
        long id,
        long sourceId,
        int ordinal,
        String content,
        String pageUrl,
        boolean disabled,
        boolean edited
) {
}
