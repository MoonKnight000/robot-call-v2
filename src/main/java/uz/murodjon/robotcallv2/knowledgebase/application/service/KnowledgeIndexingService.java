package uz.murodjon.robotcallv2.knowledgebase.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeIndexingTask;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeRetrievalUseCase;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.DocumentTextExtractorPort;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.KnowledgeChunkRepository;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.KnowledgeSourceRepository;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.TextEmbedderPort;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeChunk;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeSource;
import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeSourceType;
import uz.murodjon.robotcallv2.knowledgebase.domain.service.KnowledgeChunker;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.config.KnowledgeIndexingRabbitConfig;
import uz.murodjon.robotcallv2.shared.exception.AppException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.storage.application.dto.DownloadableFile;
import uz.murodjon.robotcallv2.storage.application.port.input.FileStorageUseCase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a registered source into searchable passages: read the document, split it, embed
 * the pieces, store them, and record what happened on the source row.
 *
 * <p>Runs off the queue rather than inside the upload request — a hundred-page PDF is
 * seconds of extraction and several round trips to the embedding model.
 *
 * <p>A source that cannot be read ends as {@code FAILED} carrying the reason, and the
 * message is acknowledged: re-delivering a corrupt PDF would only fail again. The
 * operator sees the reason on the row and can fix it and retry.
 *
 * <p>A source whose passages could not be embedded is still stored and still marked
 * {@code INDEXED} — with no vectors it is reachable through the keyword fallback, which
 * is worse retrieval but not a broken knowledge base.
 */
@Service
public class KnowledgeIndexingService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexingService.class);

    private final KnowledgeSourceRepository sourceRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final DocumentTextExtractorPort textExtractor;
    private final TextEmbedderPort textEmbedder;
    private final FileStorageUseCase fileStorage;
    private final KnowledgeRetrievalUseCase knowledgeRetrievalUseCase;
    private final RabbitTemplate rabbitTemplate;

    public KnowledgeIndexingService(KnowledgeSourceRepository sourceRepository,
                                    KnowledgeChunkRepository chunkRepository,
                                    DocumentTextExtractorPort textExtractor,
                                    TextEmbedderPort textEmbedder,
                                    FileStorageUseCase fileStorage,
                                    KnowledgeRetrievalUseCase knowledgeRetrievalUseCase,
                                    RabbitTemplate rabbitTemplate) {
        this.sourceRepository = sourceRepository;
        this.chunkRepository = chunkRepository;
        this.textExtractor = textExtractor;
        this.textEmbedder = textEmbedder;
        this.fileStorage = fileStorage;
        this.knowledgeRetrievalUseCase = knowledgeRetrievalUseCase;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Puts a source on the queue. Called after it is created, re-pointed, or retried.
     *
     * <p>Published <em>after</em> the caller's transaction commits, never inside it. The
     * broker is faster than the commit: sent inline, a consumer picks the message up,
     * looks for a row that is still uncommitted, finds nothing and drops the task — the
     * source then sits at {@code PENDING} for ever with nothing to move it.
     */
    public void enqueue(KnowledgeSource source) {
        KnowledgeIndexingTask task = new KnowledgeIndexingTask(source.companyId(), source.id());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(task);
                }
            });
        } else {
            send(task);
        }
    }

    private void send(KnowledgeIndexingTask task) {
        rabbitTemplate.convertAndSend(KnowledgeIndexingRabbitConfig.KNOWLEDGE_INDEXING_QUEUE, task);
        log.info("[company {}] Knowledge source {} queued for indexing", task.companyId(), task.sourceId());
    }

    @RabbitListener(queues = KnowledgeIndexingRabbitConfig.KNOWLEDGE_INDEXING_QUEUE)
    public void onTask(KnowledgeIndexingTask task) {
        KnowledgeSource source = sourceRepository
                .findByCompanyIdAndId(task.companyId(), task.sourceId())
                .orElse(null);
        if (source == null) {
            // Deleted between being queued and being picked up — nothing to do, and the
            // chunks went with it through the foreign key.
            log.info("Knowledge source {} is gone; skipping indexing", task.sourceId());
            return;
        }
        index(source);
    }

    private void index(KnowledgeSource source) {
        long startedAt = System.currentTimeMillis();
        sourceRepository.save(source.indexing());
        try {
            String text = readText(source);
            List<String> passages = KnowledgeChunker.chunk(text);
            if (passages.isEmpty()) {
                fail(source, ErrorCode.KNOWLEDGE_SOURCE_EMPTY.name(), ErrorCode.KNOWLEDGE_SOURCE_EMPTY.format());
                return;
            }

            List<float[]> vectors = textEmbedder.embedAll(passages);
            List<KnowledgeChunk> chunks = new ArrayList<>(passages.size());
            int embedded = 0;
            for (int i = 0; i < passages.size(); i++) {
                float[] vector = i < vectors.size() ? vectors.get(i) : null;
                if (vector != null) {
                    embedded++;
                }
                chunks.add(KnowledgeChunk.toIndex(source.id(), i, passages.get(i), vector));
            }
            chunkRepository.replaceBySourceId(source.id(), chunks);
            sourceRepository.save(source.indexed(chunks.size(), Instant.now()));
            knowledgeRetrievalUseCase.evictCompany(source.companyId());

            log.info("[company {}] Indexed knowledge source {} ('{}'): {} passages, {} embedded, {} ms",
                    source.companyId(), source.id(), source.name(), chunks.size(), embedded,
                    System.currentTimeMillis() - startedAt);
        } catch (AppException e) {
            fail(source, e.code().name(), e.getMessage());
        } catch (Exception e) {
            fail(source, ErrorCode.INTERNAL_SERVER_ERROR.name(), String.valueOf(e.getMessage()));
            log.error("[company {}] Indexing knowledge source {} failed unexpectedly",
                    source.companyId(), source.id(), e);
        }
    }

    private String readText(KnowledgeSource source) {
        if (source.sourceType() == KnowledgeSourceType.URL) {
            return textExtractor.extractFromUrl(source.url());
        }
        DownloadableFile file = fileStorage.download(source.companyId(), source.storedFileId());
        return textExtractor.extract(file.content(), source.sourceType());
    }

    private void fail(KnowledgeSource source, String failureCode, String failureMessage) {
        // The old passages go too: a source that no longer reads must stop answering with
        // what it used to say, or an operator who replaced a withdrawn price list would
        // still hear the withdrawn prices on calls.
        chunkRepository.deleteBySourceId(source.id());
        sourceRepository.save(source.failed(failureCode, trim(failureMessage)));
        knowledgeRetrievalUseCase.evictCompany(source.companyId());
        log.warn("[company {}] Knowledge source {} ('{}') failed to index: {} — {}",
                source.companyId(), source.id(), source.name(), failureCode, failureMessage);
    }

    /** The column holds 1000 characters; a provider stack trace in a message can exceed it. */
    private static String trim(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 1000 ? message : message.substring(0, 997) + "...";
    }
}
