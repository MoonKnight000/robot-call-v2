package uz.murodjon.robotcallv2.knowledgebase.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.CatalogMatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The catalog ranking (UNIVERSAL-OPERATOR-PLAN B.4). What it has to get right is the part an
 * embedding is weakest at: the one row out of thousands that carries the caller's colour
 * and size, not a neighbour one size off.
 */
class CatalogRankerTest {

    private static final String[] BRANDS = {"Nike", "Adidas", "Puma", "Reebok", "Asics",
            "Fila", "Umbro", "Kappa", "Joma", "Diadora"};
    private static final String[] PRODUCTS = {"krossovka", "futbolka", "shim", "kurtka", "kepka"};
    private static final String[] COLOURS = {"qora", "oq", "qizil", "ko'k", "yashil",
            "kulrang", "sariq", "jigarrang", "pushti", "binafsha"};

    /** 10 brands × 5 products × 10 colours × 10 sizes: 5000 rows, one per combination. */
    private static List<CatalogMatch> catalog() {
        List<CatalogMatch> rows = new ArrayList<>(5000);
        int index = 0;
        for (String brand : BRANDS) {
            for (String product : PRODUCTS) {
                for (String colour : COLOURS) {
                    for (int size = 36; size <= 45; size++) {
                        rows.add(row(skuOf(brand, product, colour, size), "nomi: " + brand + " " + product
                                + "; rangi: " + colour + "; o'lcham: " + size + "; narx: " + (100_000 + index++ * 37)));
                    }
                }
            }
        }
        return rows;
    }

    private static String skuOf(String brand, String product, String colour, int size) {
        return brand.toUpperCase() + "-" + product + "-" + colour + "-" + size;
    }

    private static CatalogMatch row(String sku, String content) {
        return new CatalogMatch(1L, sku, content, Map.of(), 0.0);
    }

    private static CatalogMatch row(String sku, String content, double similarity) {
        return new CatalogMatch(1L, sku, content, Map.of(), similarity);
    }

    @Test
    void findsTheOneRowCarryingEveryWordAmongFiveThousand() {
        List<CatalogMatch> catalog = catalog();

        long started = System.nanoTime();
        List<CatalogMatch> top = CatalogRanker.rank(catalog, CatalogRanker.terms("Puma qora 42 krossovka"), 5);
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        assertThat(top.get(0).sku()).isEqualTo(skuOf("Puma", "krossovka", "qora", 42));
        assertThat(top.get(0).score()).isEqualTo(1.0);
        assertThat(elapsedMs).isLessThan(300);
    }

    @Test
    void readsInflectedWordsAndSkipsTheWordsThatOnlyAsk() {
        List<CatalogMatch> top = CatalogRanker.rank(catalog(),
                CatalogRanker.terms("Menga qora 42 razmerli krossovkalar bormi?"), 5);

        assertThat(top).hasSize(5).allSatisfy(match -> assertThat(match.content())
                .contains("krossovka").contains("rangi: qora").contains("o'lcham: 42;"));
    }

    @Test
    void aSizeMatchesOnlyItself() {
        List<CatalogMatch> rows = List.of(row("A", "rangi: qora; o'lcham: 420"), row("B", "rangi: qora; o'lcham: 42"));

        assertThat(CatalogRanker.rank(rows, CatalogRanker.terms("qora 42"), 5).get(0).sku()).isEqualTo("B");
    }

    @Test
    void anApostropheIsNotPartOfTheWord() {
        List<CatalogMatch> rows = List.of(row("K", "rangi: ko‘k"), row("Q", "rangi: qizil"));

        assertThat(CatalogRanker.rank(rows, CatalogRanker.terms("ko'k"), 5))
                .extracting(CatalogMatch::sku).containsExactly("K");
    }

    /** The embedding rescues a row described in other words — but only on a strong match. */
    @Test
    void aRowSharingNoWordNeedsAStrongEmbeddingMatch() {
        List<CatalogMatch> rows = List.of(row("SPORT", "nomi: sport poyabzal", 0.8), row("KETTLE", "nomi: choynak", 0.3));

        assertThat(CatalogRanker.rank(rows, CatalogRanker.terms("krossovka"), 5))
                .extracting(CatalogMatch::sku).containsExactly("SPORT");
    }

    @Test
    void aRowFoundByBothListsCountsOnceWithItsBetterSimilarity() {
        List<CatalogMatch> rows = List.of(row("A", "nomi: krossovka", 0.0), row("A", "nomi: krossovka", 0.7),
                row("B", "nomi: krossovka", 0.5));

        assertThat(CatalogRanker.rank(rows, CatalogRanker.terms("krossovka"), 5))
                .extracting(CatalogMatch::sku).containsExactly("A", "B");
    }
}
