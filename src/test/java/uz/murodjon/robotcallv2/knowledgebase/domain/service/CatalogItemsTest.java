package uz.murodjon.robotcallv2.knowledgebase.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.CatalogItem;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** A feed's rows as catalog items (UNIVERSAL-OPERATOR-PLAN B.4). */
class CatalogItemsTest {

    /** A row with its columns in the order given, the way a CSV header orders them. */
    private static Map<String, String> row(String... columnsAndValues) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i + 1 < columnsAndValues.length; i += 2) {
            row.put(columnsAndValues[i], columnsAndValues[i + 1]);
        }
        return row;
    }

    @Test
    void oneItemPerKeyTheFirstRowWinning() {
        List<CatalogItem> items = CatalogItems.fromRows(7L, List.of(
                row("sku", "A1", "nomi", "Krossovka", "narx", "450000"),
                row("sku", "A1", "nomi", "Boshqa"),
                row("sku", "B7", "nomi", "Kepka"),
                row("nomi", "Kalitsiz")), null);

        assertThat(items).extracting(CatalogItem::sku).containsExactly("A1", "B7");
        assertThat(items.get(0).content()).isEqualTo("sku: A1; nomi: Krossovka; narx: 450000");
        assertThat(items.get(0).sourceId()).isEqualTo(7L);
        assertThat(items.get(0).embedding()).isNull();
    }

    @Test
    void theKeyColumnIsFoundWhateverItsCase() {
        assertThat(CatalogItems.fromRows(1L, List.of(row("SKU", "X9", "nomi", "Shim")), "sku"))
                .extracting(CatalogItem::sku).containsExactly("X9");
    }

    @Test
    void aNamedKeyColumnIsTheOneUsed() {
        assertThat(CatalogItems.fromRows(1L, List.of(row("artikul", "ART-5", "nomi", "Kurtka")), "artikul"))
                .extracting(CatalogItem::sku).containsExactly("ART-5");
    }

    /** Pointed at the wrong column, the feed would index nothing an order could name. */
    @Test
    void aFeedWithoutItsKeyColumnIsRefused() {
        ErrorCode refused = null;
        try {
            CatalogItems.fromRows(1L, List.of(row("nomi", "Krossovka")), "sku");
        } catch (ValidationException e) {
            refused = e.code();
        }

        assertThat(refused).isEqualTo(ErrorCode.CATALOG_KEY_FIELD_MISSING);
    }

    /** What a refresh compares to decide which rows it has to embed again. */
    @Test
    void onlyAChangedRowHashesDifferently() {
        String before = CatalogItems.fromRows(1L, List.of(row("sku", "A1", "narx", "450000")), null).get(0).contentHash();
        String same = CatalogItems.fromRows(1L, List.of(row("sku", "A1", "narx", "450000")), null).get(0).contentHash();
        String changed = CatalogItems.fromRows(1L, List.of(row("sku", "A1", "narx", "470000")), null).get(0).contentHash();

        assertThat(same).isEqualTo(before);
        assertThat(changed).isNotEqualTo(before);
    }
}
