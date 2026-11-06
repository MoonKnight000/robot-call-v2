package uz.murodjon.robotcallv2.knowledgebase.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeRetrievalUseCase;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.KnowledgeBaseRepository;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.KnowledgeChunkRepository;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.TextEmbedderPort;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItem;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItemMatch;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgePassage;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeQuery;
import uz.murodjon.robotcallv2.knowledgebase.domain.service.KnowledgeItemMatcher;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.config.KnowledgeBaseProperties;

import java.util.*;

/**
 * Finds what answers a caller's question, during the call: the curated answer to speak as written, or the
 * passages the model answers from.
 *
 * <p>Passages are searched in the database (pgvector, V52): the nearest by meaning and those sharing the
 * caller's words, fused by reciprocal rank. Nothing is cached per company any more, so a source the operator
 * changed or deleted is out of the next search, not out of the next cache eviction.
 *
 * <p>Everything degrades instead of failing. No embedding model, or a question that could not be embedded in
 * time, drops to the keyword ranking; no passages at all returns nothing and the turn proceeds without
 * knowledge context. A caller must never wait on this.
 */
@Service
public class KnowledgeRetrievalService implements KnowledgeRetrievalUseCase {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalService.class);

    /**
     * Below this a passage is not about the question — it is merely the least unrelated
     * thing in the document. Feeding those to the model is what makes it answer confidently
     * from the wrong paragraph, which on a debt call is worse than saying it does not know.
     */
    public static final double MIN_SEMANTIC_SCORE = 0.62;
    private static final double MIN_KEYWORD_SCORE = 0.20;
    /** A question is not a query below this; "ha", "yo'q", "mmm" match everything and nothing. */
    private static final int MIN_QUERY_CHARS = 8;
    /** The caller's latest words must be at least this long on their own, whatever context goes with them. */
    private static final int MIN_QUESTION_CHARS = 3;
    /** Reciprocal-rank fusion constant: the usual 60, so one list's top hit cannot drown the other list. */
    private static final int RRF_K = 60;
    /** How many candidates each ranking brings to the fusion, at least; the fusion keeps {@code limit}. */
    private static final int MIN_CANDIDATES = 20;
    /** How many curated items are compared by meaning; the closest few are all that can win. */
    private static final int ITEM_CANDIDATES = 10;
    /**
     * Recent question vectors by text. A turn can ask the same words twice (curated answer, then passages, or a
     * speculative turn and the final one); the second ask costs nothing. Small: it is a turn's memory, not a store.
     */
    private static final int MAX_CACHED_VECTORS = 256;

    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final TextEmbedderPort textEmbedder;
    private final KnowledgeBaseProperties knowledgeBaseProperties;
    private final Map<String, float[]> recentVectors = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
                    return size() > MAX_CACHED_VECTORS;
                }
            });

    public KnowledgeRetrievalService(KnowledgeChunkRepository chunkRepository,
                                     KnowledgeBaseRepository knowledgeBaseRepository,
                                     TextEmbedderPort textEmbedder,
                                     KnowledgeBaseProperties knowledgeBaseProperties) {
        this.chunkRepository = chunkRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.textEmbedder = textEmbedder;
        this.knowledgeBaseProperties = knowledgeBaseProperties;
    }

    @Override
    public List<KnowledgePassage> findRelevantPassages(long companyId, Long agentId, String question, int limit) {
        return findRelevantPassagesIncludingSources(companyId, agentId, List.of(), question, limit);
    }

    @Override
    public List<KnowledgePassage> findRelevantPassagesIncludingSources(long companyId, Long agentId, List<Long> extraSourceIds,
                                                                       String question, int limit) {
        return searchPassages(companyId, agentId, extraSourceIds, KnowledgeQuery.of(question), limit);
    }

    @Override
    public List<KnowledgePassage> searchPassages(long companyId, Long agentId, List<Long> extraSourceIds,
                                                 KnowledgeQuery query, int limit) {
        String question = query.question();
        String semanticText = query.semanticText() == null || query.semanticText().isBlank() ? question : query.semanticText();
        // The latest words must be more than a filler, but the length rule is read on the whole query so
        // a short follow-up ("narxi?") after context still searches.
        if (question == null || question.trim().length() < MIN_QUESTION_CHARS
                || semanticText.trim().length() < MIN_QUERY_CHARS || limit <= 0) {
            return List.of();
        }
        List<Long> extra = extraSourceIds == null ? List.of() : extraSourceIds;
        int candidates = Math.max(limit * 4, MIN_CANDIDATES);

        // Keywords stay on the latest words: earlier turns would make every passage that merely
        // shares a word with the conversation a keyword hit.
        List<String> terms = findTerms(question);
        List<KnowledgePassage> keywordHits = terms.isEmpty() ? List.of()
                : scoreByKeywords(chunkRepository.findByTerms(companyId, agentId, extra, terms, candidates), terms);
        float[] queryVector = embed(semanticText);
        if (queryVector == null) {
            return keywordHits.stream().limit(limit).toList();
        }
        double minScore = query.minSemanticScore() != null ? query.minSemanticScore() : MIN_SEMANTIC_SCORE;
        List<KnowledgePassage> semanticHits = chunkRepository.findNearest(companyId, agentId, extra, queryVector, candidates)
                .stream()
                .filter(passage -> passage.score() >= minScore)
                .toList();
        // Hybrid (NEXT-PLAN A.3): the passage an embedding ranks low — a contract number, a
        // product code, a name — is often exactly the one whose words the caller said.
        return fuse(semanticHits, keywordHits, limit);
    }

    @Override
    public KnowledgeItemMatch findCuratedAnswer(long companyId, Long agentId, String question, String language) {
        if (question == null || question.trim().length() < MIN_QUESTION_CHARS) {
            return null;
        }
        List<KnowledgeItem> items = knowledgeBaseRepository.findAllActiveByCompanyIdAndAgentId(companyId, agentId);
        if (items.isEmpty()) {
            return null;
        }
        // The caller's own words only, not the conversation: an answer read out verbatim must be about what
        // was just asked, not about the topic two turns ago.
        float[] vector = question.trim().length() >= MIN_QUERY_CHARS ? embed(question) : null;
        Map<Long, Double> similarity = vector == null ? Map.of()
                : knowledgeBaseRepository.findSimilarities(companyId, agentId, vector, ITEM_CANDIDATES);
        KnowledgeItemMatch match = KnowledgeItemMatcher.findBest(question, items, similarity,
                knowledgeBaseProperties.itemMinScore(), language);
        if (match != null) {
            log.debug("[company {}] Curated answer '{}' chosen ({}, score {})", companyId, match.key(),
                    match.byKeyword() ? "keyword" : "meaning", String.format("%.2f", match.score()));
        }
        return match;
    }

    /** The question's vector, from the recent ones when the same words were just embedded; null when unavailable. */
    private float[] embed(String text) {
        float[] cached = recentVectors.get(text);
        if (cached != null) {
            return cached;
        }
        float[] vector = textEmbedder.embedQuery(text);
        if (vector != null) {
            recentVectors.put(text, vector);
        }
        return vector;
    }

    /**
     * Reciprocal rank fusion of the semantic and the keyword ranking: each passage scores
     * the sum of 1/(k + rank) over the lists it appears in, so one both lists found rises
     * above one only either found. The passage keeps its own best score, and identical
     * text indexed from two sources counts once.
     */
    private static List<KnowledgePassage> fuse(List<KnowledgePassage> semantic, List<KnowledgePassage> keyword,
                                               int limit) {
        Map<String, Double> fused = new LinkedHashMap<>();
        Map<String, KnowledgePassage> best = new LinkedHashMap<>();
        for (List<KnowledgePassage> ranking : List.of(semantic, keyword)) {
            for (int rank = 0; rank < ranking.size(); rank++) {
                KnowledgePassage passage = ranking.get(rank);
                fused.merge(passage.content(), 1.0 / (RRF_K + rank + 1), Double::sum);
                best.merge(passage.content(), passage, (kept, other) -> kept.score() >= other.score() ? kept : other);
            }
        }
        return fused.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> best.get(entry.getKey()))
                .toList();
    }

    /** The question's words worth matching: four letters or more, apostrophes dropped as the text index does. */
    private static List<String> findTerms(String question) {
        List<String> terms = new ArrayList<>();
        for (String word : normalize(question).split(" ")) {
            if (word.length() >= 4 && !terms.contains(word)) {
                terms.add(word);
            }
        }
        return terms;
    }

    /**
     * The database found these by any shared word; the score is what share of the question's words the
     * passage contains. Held to a higher bar than it looks — a fifth of a sentence's words is already a
     * weak signal in an inflected language.
     */
    private static List<KnowledgePassage> scoreByKeywords(List<KnowledgePassage> candidates, List<String> terms) {
        List<KnowledgePassage> hits = new ArrayList<>();
        for (KnowledgePassage candidate : candidates) {
            String content = normalize(candidate.content());
            int matched = 0;
            for (String term : terms) {
                if (content.contains(term)) {
                    matched++;
                }
            }
            double score = (double) matched / terms.size();
            if (score >= MIN_KEYWORD_SCORE) {
                hits.add(candidate.withScore(score));
            }
        }
        hits.sort(Comparator.comparingDouble(KnowledgePassage::score).reversed());
        return hits;
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("['`ʻʼ‘’]", "")
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }
}
