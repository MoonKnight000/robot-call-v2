package uz.murodjon.robotcallv2.knowledgebase.infrastructure.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.knowledgebase.application.port.output.TextEmbedderPort;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.config.KnowledgeBaseProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Embeds knowledge passages with whichever {@link EmbeddingModel} the configured AI
 * provider registered — the same key the dialog model already runs on, so nothing extra
 * has to be provisioned.
 *
 * <p>The model is optional. A build with no embedding model wired must still accept
 * uploads and still run calls: {@link #isAvailable()} is false, chunks are stored with no
 * vector, and search falls back to keywords. That is worse retrieval, not a broken agent.
 */
@Component
public class SpringAiTextEmbedderAdapter implements TextEmbedderPort {

    private static final Logger log = LoggerFactory.getLogger(SpringAiTextEmbedderAdapter.class);

    /**
     * How many passages go in one call. Providers cap both the batch size and the total
     * tokens per request; 32 chunks of ~900 characters stays under both with room to
     * spare, and a document of any size is then a handful of calls rather than hundreds.
     */
    private static final int BATCH_SIZE = 32;

    /** Runs a question's embedding so the caller's thread can stop waiting on it; one virtual thread per query. */
    private static final ExecutorService QUERY_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final KnowledgeBaseProperties knowledgeBaseProperties;
    /** Set after the first failure so an outage is reported once, not once per batch. */
    private volatile boolean failureLogged;

    public SpringAiTextEmbedderAdapter(ObjectProvider<EmbeddingModel> embeddingModelProvider,
                                       KnowledgeBaseProperties knowledgeBaseProperties) {
        this.embeddingModelProvider = embeddingModelProvider;
        this.knowledgeBaseProperties = knowledgeBaseProperties;
    }

    @Override
    public boolean isAvailable() {
        return embeddingModelProvider.getIfAvailable() != null;
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model == null) {
            return nulls(texts.size());
        }

        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += BATCH_SIZE) {
            List<String> batch = texts.subList(from, Math.min(from + BATCH_SIZE, texts.size()));
            try {
                List<float[]> embedded = model.embed(batch);
                // A provider that answers with the wrong count would silently shift every
                // vector onto the wrong passage, which is worse than having none.
                if (embedded == null || embedded.size() != batch.size()) {
                    log.warn("Embedding model returned {} vectors for {} passages; skipping the batch",
                            embedded == null ? 0 : embedded.size(), batch.size());
                    vectors.addAll(nulls(batch.size()));
                } else {
                    vectors.addAll(embedded);
                }
            } catch (Exception e) {
                logFailureOnce("embed a batch of knowledge passages", e);
                vectors.addAll(nulls(batch.size()));
            }
        }
        return vectors;
    }

    @Override
    public float[] embedQuery(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model == null) {
            return null;
        }
        // A caller is waiting on this: past the timeout the search goes on with keywords alone
        // rather than holding the turn on a slow embedding provider.
        CompletableFuture<float[]> embedding = CompletableFuture.supplyAsync(() -> model.embed(text), QUERY_EXECUTOR);
        try {
            return embedding.get(knowledgeBaseProperties.queryEmbedTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            embedding.cancel(true);
            log.warn("Embedding a caller question took over {} ms — knowledge search uses keywords for this turn",
                    knowledgeBaseProperties.queryEmbedTimeoutMs());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            embedding.cancel(true);
            return null;
        } catch (ExecutionException e) {
            logFailureOnce("embed a caller question", e.getCause() instanceof Exception cause ? cause : e);
            return null;
        }
    }

    private void logFailureOnce(String what, Exception e) {
        if (!failureLogged) {
            failureLogged = true;
            log.warn("Embedding model failed to {} — knowledge search falls back to keywords: {}",
                    what, e.getMessage());
        } else {
            log.debug("Embedding model failed to {}: {}", what, e.getMessage());
        }
    }

    private static List<float[]> nulls(int count) {
        return Collections.nCopies(count, null);
    }
}
