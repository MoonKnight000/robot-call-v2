package uz.murodjon.robotcallv2.knowledgebase.application.dto;

/**
 * How often one source's passages were handed to the model.
 *
 * @param hits searches it contributed a passage to
 */
public record KnowledgeSourceUsageRow(
        long sourceId,
        String sourceName,
        int hits
) {
}
