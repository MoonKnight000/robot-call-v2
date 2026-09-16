package uz.murodjon.robotcallv2.knowledgebase.domain.entity;

import java.util.Map;

/**
 * One row of a product catalog — a {@code STRUCTURED_FEED} source (UNIVERSAL-OPERATOR-PLAN B.4).
 *
 * @param sku         the row's key: what an order tool is handed, never something the model made up
 * @param content     the row as one line of text, "nomi: …; rangi: …; narx: …" — what is
 *                    embedded, matched and read to the model
 * @param attributes  every non-blank column of the row, as it came
 * @param contentHash SHA-256 of {@code content}: a refresh embeds again only the rows whose text changed
 * @param embedding   null when the embedding model could not be reached; the row is still found by its words
 */
public record CatalogItem(
        long sourceId,
        String sku,
        String content,
        Map<String, String> attributes,
        String contentHash,
        float[] embedding
) {
    public CatalogItem withEmbedding(float[] vector) {
        return new CatalogItem(sourceId, sku, content, attributes, contentHash, vector);
    }
}
