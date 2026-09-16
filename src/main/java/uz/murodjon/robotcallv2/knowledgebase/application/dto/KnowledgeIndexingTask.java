package uz.murodjon.robotcallv2.knowledgebase.application.dto;

/** One source to index, as it travels over the queue. */
public record KnowledgeIndexingTask(long companyId, long sourceId) {

    /** The RabbitMQ queue indexing tasks travel on. */
    public static final String QUEUE = "knowledge.indexing";
}
