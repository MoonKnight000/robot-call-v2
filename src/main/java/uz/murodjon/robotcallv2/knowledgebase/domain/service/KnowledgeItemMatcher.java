package uz.murodjon.robotcallv2.knowledgebase.domain.service;

import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItem;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItemMatch;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Picks the curated answer for what the caller said — or none. A curated answer is spoken word for word,
 * so a wrong pick is worse than no pick: the model would at least have answered the question asked.
 *
 * <p>An item is a candidate two ways:
 * <ul>
 *   <li><b>by keyword</b> — its key or one of its keywords starts a word of the caller's sentence. Starting a
 *       word, not merely appearing inside one: "kim" must not match "hokim", while "to'lov" still matches
 *       "to'lovni". A keyword under {@link #MIN_TERM_CHARS} letters is ignored — it matches too much.</li>
 *   <li><b>by meaning</b> — its embedding is at least {@code minScore} close to the question.</li>
 * </ul>
 * A keyword candidate wins over a meaning-only one; among keyword candidates the one closer in meaning wins,
 * then the one whose matched term is longer (the more specific).
 */
public final class KnowledgeItemMatcher {

    static final int MIN_TERM_CHARS = 3;

    private KnowledgeItemMatcher() {
    }

    /**
     * @param similarity item id → cosine to the question; an item missing here has no vector (or the model was
     *                   not reachable) and can be matched by keyword only
     */
    public static KnowledgeItemMatch findBest(String question, List<KnowledgeItem> items, Map<Long, Double> similarity,
                                              double minScore, String language) {
        if (question == null || question.isBlank() || items == null || items.isEmpty()) {
            return null;
        }
        String words = " " + normalize(question) + " ";
        KnowledgeItem best = null;
        boolean bestByKeyword = false;
        double bestScore = -1;
        int bestTermLength = 0;
        for (KnowledgeItem item : items) {
            double score = similarity.getOrDefault(item.id(), 0.0);
            int termLength = findLongestTerm(words, item);
            boolean byKeyword = termLength > 0;
            if (!byKeyword && score < minScore) {
                continue;
            }
            boolean better = best == null
                    || (byKeyword && !bestByKeyword)
                    || (byKeyword == bestByKeyword && (score > bestScore
                        || (score == bestScore && termLength > bestTermLength)));
            if (better) {
                best = item;
                bestByKeyword = byKeyword;
                bestScore = score;
                bestTermLength = termLength;
            }
        }
        return best == null ? null
                : new KnowledgeItemMatch(best.id(), best.key(), best.answerForLanguage(language),
                        Math.max(bestScore, 0.0), bestByKeyword);
    }

    /** The length of the item's longest key or keyword that starts a word of {@code words}; 0 when none does. */
    private static int findLongestTerm(String words, KnowledgeItem item) {
        int longest = 0;
        longest = Math.max(longest, matchedLength(words, item.key()));
        if (item.keywords() != null && !item.keywords().isBlank()) {
            // Split on spaces too, as keywords always were: existing items list words, not phrases.
            for (String keyword : item.keywords().split("[,;\\s]+")) {
                longest = Math.max(longest, matchedLength(words, keyword));
            }
        }
        return longest;
    }

    private static int matchedLength(String words, String term) {
        if (term == null) {
            return 0;
        }
        String normalized = normalize(term);
        if (normalized.replace(" ", "").length() < MIN_TERM_CHARS) {
            return 0;
        }
        return words.contains(" " + normalized) ? normalized.length() : 0;
    }

    /** Lower case, apostrophe variants dropped (to'lov = tolov), anything else that is not a letter or digit a space. */
    public static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("['`ʻʼ‘’]", "")
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }
}
