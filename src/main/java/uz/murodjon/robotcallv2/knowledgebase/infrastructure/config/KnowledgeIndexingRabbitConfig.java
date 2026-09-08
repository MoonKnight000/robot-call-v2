package uz.murodjon.robotcallv2.knowledgebase.infrastructure.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The queue that carries a knowledge source from "uploaded" to "indexed".
 *
 * <p>Indexing reads a file out of MinIO or fetches a web page, then makes several calls
 * to the embedding model — seconds of work that must not be done inside the HTTP request
 * that uploaded it, and must survive a restart halfway through. A durable queue gives
 * both, and the JSON converter from the dialer's {@code RabbitConfig} serializes the
 * message.
 *
 * <p>Failures are the indexer's own business, not the broker's: a source that cannot be
 * read is recorded as {@code FAILED} with a reason and the message is acknowledged, so a
 * corrupt PDF does not spin forever. {@code POST /sources/{id}/retry} re-queues it.
 */
@Configuration
public class KnowledgeIndexingRabbitConfig {

    public static final String KNOWLEDGE_INDEXING_QUEUE = "knowledge.indexing";

    @Bean
    public Queue knowledgeIndexingQueue() {
        return QueueBuilder.durable(KNOWLEDGE_INDEXING_QUEUE).build();
    }
}
