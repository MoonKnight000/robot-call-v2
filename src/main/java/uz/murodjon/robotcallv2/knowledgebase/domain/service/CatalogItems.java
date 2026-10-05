package uz.murodjon.robotcallv2.knowledgebase.domain.service;

import uz.murodjon.robotcallv2.knowledgebase.domain.entity.CatalogItem;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * A catalog feed's rows as the items the agent searches (UNIVERSAL-OPERATOR-PLAN B.4).
 *
 * <p>A row is kept whole rather than split like prose: "qora 42 krossovka" is answered by
 * one row, and a passage boundary through it would separate the size from the shoe.
 */
public final class CatalogItems {

    /** The key column a feed is read by when its source names none. */
    public static final String DEFAULT_KEY_FIELD = "sku";

    /** The width of {@code catalog_item.sku}. */
    private static final int MAX_SKU_CHARS = 200;

    /** A row is a line of text, not a document: past this nothing is read to anyone. */
    private static final int MAX_CONTENT_CHARS = 1000;

    private CatalogItems() {
    }

    /**
     * One item per distinct key — the first row carrying a key wins, a row without one is
     * left out.
     *
     * @throws ValidationException when no row carries the key column at all: the feed is
     *                             pointed at the wrong column, and indexing it would store
     *                             nothing an order could name
     */
    public static List<CatalogItem> fromRows(long sourceId, List<Map<String, String>> rows, String keyField) {
        String key = keyField == null || keyField.isBlank() ? DEFAULT_KEY_FIELD : keyField.trim();
        Map<String, CatalogItem> items = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String sku = valueOf(row, key);
            if (sku == null || sku.length() > MAX_SKU_CHARS || items.containsKey(sku)) {
                continue;
            }
            Map<String, String> attributes = nonBlank(row);
            String content = render(attributes);
            items.put(sku, new CatalogItem(sourceId, sku, content, Map.copyOf(attributes), hash(content), null));
        }
        if (items.isEmpty() && !rows.isEmpty()) {
            throw new ValidationException(ErrorCode.CATALOG_KEY_FIELD_MISSING, key);
        }
        return List.copyOf(items.values());
    }

    /** The row as one line, column by column: "sku: A1; nomi: Krossovka; narx: 450000". */
    static String render(Map<String, String> attributes) {
        StringJoiner line = new StringJoiner("; ");
        attributes.forEach((column, value) -> line.add(column + ": " + value));
        String content = line.toString();
        return content.length() <= MAX_CONTENT_CHARS ? content : content.substring(0, MAX_CONTENT_CHARS);
    }

    /** A column is found whatever its case — "SKU", "Sku" and "sku" are one column to the person who named it. */
    private static String valueOf(Map<String, String> row, String column) {
        for (Map.Entry<String, String> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().trim().toLowerCase(Locale.ROOT)
                    .equals(column.toLowerCase(Locale.ROOT))) {
                String value = entry.getValue();
                return value == null || value.isBlank() ? null : value.trim();
            }
        }
        return null;
    }

    private static Map<String, String> nonBlank(Map<String, String> row) {
        Map<String, String> kept = new LinkedHashMap<>();
        row.forEach((column, value) -> {
            if (column != null && !column.isBlank() && value != null && !value.isBlank()) {
                kept.put(column.trim(), value.trim());
            }
        });
        return kept;
    }

    private static String hash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", e);
        }
    }
}
