package uz.murodjon.robotcallv2.knowledgebase.infrastructure.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.knowledgebase.application.port.output.TextEmbedderPort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Embeds knowledge passages with whichever {@link EmbeddingModel} the configured AI
 * provider registered — the same key the dialog model already runs on, so nothing extra
 * has to be provisioned.
 *
 * <p>The model is optional. A build with no embedding model wired must still accept
 * uploads and still run calls: {@link #available()} is false, chunks are stored with no
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

    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    /** Set after the first failure so an outage is reported once, not once per batch. */
    private volatile boolean failureLogged;

    public SpringAiTextEmbedderAdapter(ObjectProvider<EmbeddingModel> embeddingModelProvider) {
        this.embeddingModelProvider = embeddingModelProvider;
    }

    @Override
    public boolean available() {
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
        try {
            return model.embed(text);
        } catch (Exception e) {
            logFailureOnce("embed a caller question", e);
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
