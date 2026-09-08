package uz.murodjon.robotcallv2.knowledgebase.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeRetrievalUseCase;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.KnowledgeChunkRepository;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.TextEmbedderPort;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.IndexedChunk;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgePassage;

import java.util.*;

/**
 * Finds the passages that answer a caller's question, during the call.
 *
 * <p>Search is cosine similarity in this process, not in the database. A company's
 * knowledge base is thousands of passages, not millions: at that size scanning them is
 * a fraction of a millisecond, and doing it here is what lets the database stay a stock
 * Postgres with no vector extension. The chunks are read once per company and held in
 * {@link #cache}; indexing evicts the entry rather than letting a call see stale text.
 *
 * <p>Everything degrades instead of failing. No embedding model, or a question that could
 * not be embedded, drops to keyword overlap; no chunks at all returns nothing and the turn
 * proceeds without knowledge context. A caller must never wait on this.
 */
@Service
public class KnowledgeRetrievalService implements KnowledgeRetrievalUseCase {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalService.class);

    /**
     * Below this a passage is not about the question — it is merely the least unrelated
     * thing in the document. Feeding those to the model is what makes it answer confidently
     * from the wrong paragraph, which on a debt call is worse than saying it does not know.
     */
    private static final double MIN_SEMANTIC_SCORE = 0.62;
    private static final double MIN_KEYWORD_SCORE = 0.20;
    /** A question is not a query below this; "ha", "yo'q", "mmm" match everything and nothing. */
    private static final int MIN_QUERY_CHARS = 8;

    private final KnowledgeChunkRepository chunkRepository;
    private final TextEmbedderPort textEmbedder;

    /**
     * How many companies' passages are held at once. Every entry is that company's whole
     * knowledge base in memory, so this is the ceiling on what the cache can cost: past
     * it the least recently used company is dropped and pays one query on its next call.
     * Well above how many companies are ever dialling at the same time.
     */
    private static final int MAX_CACHED_COMPANIES = 64;

    /**
     * Company id → every indexed passage it owns. Keyed by company rather than by agent
     * because the company-wide sources would otherwise be held once per agent; the
     * per-agent narrowing is a filter over this list, not a second entry.
     *
     * <p>Access-ordered and bounded: without a bound this grows with the number of
     * companies that have ever taken a call and never shrinks, which on a shared instance
     * is every company. Synchronised because {@link LinkedHashMap} in access order
     * restructures itself on a read, so even lookups cannot run concurrently.
     */
    private final Map<Long, List<IndexedChunk>> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, List<IndexedChunk>> eldest) {
                    return size() > MAX_CACHED_COMPANIES;
                }
            });

    public KnowledgeRetrievalService(KnowledgeChunkRepository chunkRepository, TextEmbedderPort textEmbedder) {
        this.chunkRepository = chunkRepository;
        this.textEmbedder = textEmbedder;
    }

    @Override
    public List<KnowledgePassage> findRelevantPassages(long companyId, Long agentId, String question, int limit) {
        if (question == null || question.trim().length() < MIN_QUERY_CHARS || limit <= 0) {
            return List.of();
        }
        List<IndexedChunk> chunks = visibleChunks(companyId, agentId);
        if (chunks.isEmpty()) {
            return List.of();
        }

        float[] queryVector = textEmbedder.embedQuery(question);
        List<KnowledgePassage> hits = queryVector != null
                ? scoreBySimilarity(chunks, queryVector)
                : scoreByKeywords(chunks, question);

        return hits.stream()
                .sorted(Comparator.comparingDouble(KnowledgePassage::score).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public void evictCompany(long companyId) {
        cache.remove(companyId);
    }

    private List<IndexedChunk> visibleChunks(long companyId, Long agentId) {
        // Read outside the map's lock rather than inside computeIfAbsent. The cache has to
        // be guarded to stay access-ordered, and holding that guard across a query would
        // stall every other call's lookup behind one company's read — on a path where a
        // caller is waiting, that is the worse failure. Two calls arriving together on a
        // cold company both query, which costs one extra read and nothing else.
        List<IndexedChunk> all = cache.get(companyId);
        if (all == null) {
            all = chunkRepository.findIndexedByCompanyId(companyId);
            log.info("[company {}] Knowledge search loaded {} passages", companyId, all.size());
            cache.put(companyId, all);
        }
        if (agentId == null || all.isEmpty()) {
            return all;
        }
        // The cache holds the whole company; narrowing to this agent's own sources plus
        // the company-wide ones happens here, on a list that is already in memory.
        return all.stream().filter(chunk -> chunk.visibleTo(agentId)).toList();
    }

    private static List<KnowledgePassage> scoreBySimilarity(List<IndexedChunk> chunks, float[] query) {
        double queryNorm = norm(query);
        if (queryNorm == 0.0) {
            return List.of();
        }
        List<KnowledgePassage> hits = new ArrayList<>();
        for (IndexedChunk chunk : chunks) {
            float[] vector = chunk.embedding();
            if (vector == null || vector.length != query.length) {
                continue;
            }
            double score = cosine(query, vector, queryNorm);
            if (score >= MIN_SEMANTIC_SCORE) {
                hits.add(new KnowledgePassage(chunk.sourceId(), chunk.sourceName(), chunk.content(), score));
            }
        }
        return hits;
    }

    /**
     * The fallback when there are no vectors: what share of the question's words the
     * passage contains. Crude, and deliberately held to a higher bar than it looks —
     * a fifth of a sentence's words is already a weak signal in an inflected language.
     */
    private static List<KnowledgePassage> scoreByKeywords(List<IndexedChunk> chunks, String question) {
        String[] words = question.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}']+");
        List<String> terms = new ArrayList<>();
        for (String word : words) {
            if (word.length() >= 4) {
                terms.add(word);
            }
        }
        if (terms.isEmpty()) {
            return List.of();
        }

        List<KnowledgePassage> hits = new ArrayList<>();
        for (IndexedChunk chunk : chunks) {
            String content = chunk.content().toLowerCase(Locale.ROOT);
            int matched = 0;
            for (String term : terms) {
                if (content.contains(term)) {
                    matched++;
                }
            }
            double score = (double) matched / terms.size();
            if (score >= MIN_KEYWORD_SCORE) {
                hits.add(new KnowledgePassage(chunk.sourceId(), chunk.sourceName(), chunk.content(), score));
            }
        }
        return hits;
    }

    private static double cosine(float[] query, float[] candidate, double queryNorm) {
        double dot = 0.0;
        double candidateNorm = 0.0;
        for (int i = 0; i < query.length; i++) {
            dot += (double) query[i] * candidate[i];
            candidateNorm += (double) candidate[i] * candidate[i];
        }
        candidateNorm = Math.sqrt(candidateNorm);
        return candidateNorm == 0.0 ? 0.0 : dot / (queryNorm * candidateNorm);
    }

    private static double norm(float[] vector) {
        double sum = 0.0;
        for (float value : vector) {
            sum += (double) value * value;
        }
        return Math.sqrt(sum);
    }
}
