package uz.murodjon.robotcallv2.knowledgebase.application.dto;

/** One source to index, as it travels over the queue. */
public record KnowledgeIndexingTask(long companyId, long sourceId) {
}
