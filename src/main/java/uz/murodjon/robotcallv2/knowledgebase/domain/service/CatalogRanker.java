package uz.murodjon.robotcallv2.knowledgebase.domain.service;

import uz.murodjon.robotcallv2.knowledgebase.domain.entity.CatalogMatch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Orders the catalog rows a search gathered by how much of what the caller said each one
 * carries (UNIVERSAL-OPERATOR-PLAN B.4).
 *
 * <p>Words first, embedding second, on purpose. An embedding knows a krossovka from a
 * kurtka and is close to useless between size 41 and size 42 — and the size is the part of
 * "qora 42 krossovka" the order is about. So a row is ranked by the share of the caller's
 * words it carries, and the embedding breaks ties and rescues a row the caller described
 * in words the catalog does not use.
 *
 * <p>A number matches only itself: "42" is a size, and "420" is not it. A word of four or
 * more letters also matches as a prefix either way, because Uzbek and Russian inflect at
 * the end — "krossovkalar" and "krossovkani" are the catalog's "krossovka".
 */
public final class CatalogRanker {

    /** Below this an embedding match is the least unrelated row, not a match — kept only if its words matched. */
    private static final double MIN_SIMILARITY = 0.6;

    /** Shortest word that may match as a prefix. */
    private static final int MIN_PREFIX = 4;

    /** Words that ask for something without naming it. */
    private static final Set<String> FILLERS = Set.of(
            "menga", "kerak", "bormi", "bor", "sizda", "bitta", "iltimos", "qanaqa", "qanday",
            "мне", "нужно", "нужен", "нужна", "есть", "вас", "пожалуйста", "какой", "какие");

    /** Every apostrophe a keyboard or a transcriber produces; checked before the letter test, as they are letters to Unicode. */
    private static final String APOSTROPHES = "'`ʻʼ‘’";

    private CatalogRanker() {
    }

    /** The caller's words a row is matched on: distinct, in order, fillers left out. */
    public static List<String> terms(String query) {
        Set<String> terms = new LinkedHashSet<>();
        for (String token : tokens(query)) {
            if (!FILLERS.contains(token)) {
                terms.add(token);
            }
        }
        return List.copyOf(terms);
    }

    /** Lower case, apostrophes dropped, split on anything else: words of two letters or more, and every number. */
    public static List<String> tokens(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null) {
            return tokens;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder word = new StringBuilder();
        for (int i = 0; i <= lower.length(); i++) {
            char c = i < lower.length() ? lower.charAt(i) : ' ';
            if (APOSTROPHES.indexOf(c) >= 0) {
                continue;
            }
            if (Character.isLetterOrDigit(c)) {
                word.append(c);
            } else if (!word.isEmpty()) {
                if (word.length() >= 2 || isNumber(word)) {
                    tokens.add(word.toString());
                }
                word.setLength(0);
            }
        }
        return tokens;
    }

    /**
     * The best {@code limit} rows, most of the caller's words first, then by embedding
     * similarity. A row found twice (by its words and by its vector) counts once, with the
     * better similarity. A row that shares no word is kept only on a strong embedding match.
     *
     * @return the rows with {@code score} set to the share of the words each carries
     */
    public static List<CatalogMatch> rank(Collection<CatalogMatch> candidates, List<String> terms, int limit) {
        Map<String, CatalogMatch> distinct = new LinkedHashMap<>();
        for (CatalogMatch candidate : candidates) {
            distinct.merge(candidate.sourceId() + ":" + candidate.sku(), candidate,
                    (kept, other) -> kept.score() >= other.score() ? kept : other);
        }
        record Scored(CatalogMatch match, double coverage) {
        }
        List<Scored> scored = new ArrayList<>();
        for (CatalogMatch match : distinct.values()) {
            double coverage = coverage(terms, tokens(match.content()));
            if (coverage > 0 || match.score() >= MIN_SIMILARITY) {
                scored.add(new Scored(match, coverage));
            }
        }
        return scored.stream()
                .sorted(Comparator.comparingDouble(Scored::coverage).reversed()
                        .thenComparing(Comparator.comparingDouble((Scored s) -> s.match().score()).reversed()))
                .limit(limit)
                .map(s -> new CatalogMatch(s.match().sourceId(), s.match().sku(), s.match().content(),
                        s.match().attributes(), s.coverage()))
                .toList();
    }

    /** The share of {@code terms} the row's words carry, 0..1. */
    static double coverage(List<String> terms, Collection<String> rowTokens) {
        if (terms.isEmpty()) {
            return 0;
        }
        Set<String> row = new HashSet<>(rowTokens);
        int matched = 0;
        for (String term : terms) {
            if (matches(term, row)) {
                matched++;
            }
        }
        return (double) matched / terms.size();
    }

    private static boolean matches(String term, Set<String> rowTokens) {
        if (rowTokens.contains(term)) {
            return true;
        }
        if (isNumber(term) || term.length() < MIN_PREFIX) {
            return false;
        }
        for (String token : rowTokens) {
            if (token.length() >= MIN_PREFIX && !isNumber(token)
                    && (token.startsWith(term) || term.startsWith(token))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNumber(CharSequence text) {
        return !text.isEmpty() && text.chars().allMatch(Character::isDigit);
    }
}
