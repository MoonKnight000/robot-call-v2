package uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.CatalogItemRepository;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.CatalogItem;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.CatalogMatch;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalog rows in {@code catalog_item} (V11): the vector index answers the nearest rows,
 * the text index the rows that share the caller's words.
 *
 * <p>Plain JDBC rather than an entity: the embedding is a pgvector column, which Hibernate
 * would need an extra module to map and {@code ddl-auto: validate} would refuse without it,
 * and nothing here is ever loaded to be changed — rows are replaced whole and read as
 * search results.
 */
@Component
public class CatalogItemRepositoryAdapter implements CatalogItemRepository {

    /**
     * The width of {@code catalog_item.embedding}. A longer vector is cut to it and
     * normalised again — the Matryoshka embeddings Gemini serves are trained to be cut this
     * way, which is what their own reduced output sizes are. A shorter one is not stored:
     * the row is still found by its words.
     */
    static final int DIMENSIONS = 768;

    private static final int INSERT_BATCH = 500;

    private final ObjectMapper objectMapper;
    private static final TypeReference<Map<String, String>> ATTRIBUTES = new TypeReference<>() {};

    /** Every search's narrowing: this company's indexed sources that this agent may use. */
    private static final String VISIBLE = """
             FROM catalog_item ci
             JOIN knowledge_source s ON s.id = ci.source_id
            WHERE ci.company_id = :companyId
              AND s.status = 'INDEXED'
              AND (CAST(:agentId AS BIGINT) IS NULL OR s.agent_id IS NULL OR s.agent_id = :agentId)
            """;

    private static final String MATCH_COLUMNS = "SELECT ci.source_id, ci.sku, ci.content, ci.attributes::text AS attributes, ";

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;

    public CatalogItemRepositoryAdapter(JdbcClient jdbcClient, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.jdbcClient = jdbcClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void replaceBySourceId(long companyId, long sourceId, List<CatalogItem> items) {
        jdbcTemplate.update("DELETE FROM catalog_item WHERE source_id = ?", sourceId);
        for (int from = 0; from < items.size(); from += INSERT_BATCH) {
            List<CatalogItem> batch = items.subList(from, Math.min(from + INSERT_BATCH, items.size()));
            jdbcTemplate.batchUpdate("""
                    INSERT INTO catalog_item (source_id, company_id, sku, content, attributes, content_hash, embedding)
                    VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, CAST(? AS vector))
                    """, batch, batch.size(), (statement, item) -> {
                statement.setLong(1, sourceId);
                statement.setLong(2, companyId);
                statement.setString(3, item.sku());
                statement.setString(4, item.content());
                statement.setString(5, toJson(item.attributes()));
                statement.setString(6, item.contentHash());
                statement.setString(7, toVectorLiteral(item.embedding()));
            });
        }
    }

    @Override
    public void deleteBySourceId(long sourceId) {
        jdbcTemplate.update("DELETE FROM catalog_item WHERE source_id = ?", sourceId);
    }

    @Override
    public Map<String, float[]> findEmbeddingsBySourceId(long sourceId) {
        Map<String, float[]> vectors = new HashMap<>();
        jdbcClient.sql("SELECT content_hash, embedding::text AS embedding FROM catalog_item "
                        + "WHERE source_id = :sourceId AND embedding IS NOT NULL")
                .param("sourceId", sourceId)
                .query((rs, rowNum) -> Map.entry(rs.getString("content_hash"), fromVectorLiteral(rs.getString("embedding"))))
                .list()
                .forEach(entry -> vectors.putIfAbsent(entry.getKey(), entry.getValue()));
        return vectors;
    }

    /**
     * In a transaction for the one setting it needs. A company is a slice of one shared
     * index: without an iterative scan the index hands back its nearest rows across every
     * company, the filter drops the others, and a small catalog comes back short (pgvector
     * 0.8+). The order it relaxes does not matter — {@code CatalogRanker} orders the result.
     */
    @Override
    @Transactional(readOnly = true)
    public List<CatalogMatch> findNearest(long companyId, Long agentId, float[] query, int limit) {
        String vector = toVectorLiteral(query);
        if (vector == null) {
            return List.of();
        }
        jdbcClient.sql("SET LOCAL hnsw.iterative_scan = relaxed_order").update();
        return jdbcClient.sql(MATCH_COLUMNS + "1 - (ci.embedding <=> CAST(:query AS vector)) AS score" + VISIBLE
                        + " AND ci.embedding IS NOT NULL ORDER BY ci.embedding <=> CAST(:query AS vector) LIMIT :limit")
                .param("companyId", companyId)
                .param("agentId", agentId, Types.BIGINT)
                .param("query", vector)
                .param("limit", limit)
                .query(this::toMatch)
                .list();
    }

    @Override
    public List<CatalogMatch> findByTerms(long companyId, Long agentId, List<String> terms, int limit) {
        String tsQuery = toTsQuery(terms);
        if (tsQuery.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql(MATCH_COLUMNS + "0.0 AS score" + VISIBLE
                        + " AND ci.search_text @@ to_tsquery('simple', :terms)"
                        + " ORDER BY ts_rank(ci.search_text, to_tsquery('simple', :terms)) DESC LIMIT :limit")
                .param("companyId", companyId)
                .param("agentId", agentId, Types.BIGINT)
                .param("terms", tsQuery)
                .param("limit", limit)
                .query(this::toMatch)
                .list();
    }

    @Override
    public boolean existsByCompanyIdAndAgentId(long companyId, Long agentId) {
        Boolean exists = jdbcClient.sql("SELECT EXISTS (SELECT 1" + VISIBLE + ")")
                .param("companyId", companyId)
                .param("agentId", agentId, Types.BIGINT)
                .query(Boolean.class)
                .single();
        return Boolean.TRUE.equals(exists);
    }

    /**
     * The caller's words as one OR query, only to gather candidates. A word matches by its
     * first five letters as a prefix — Uzbek and Russian inflect at the end, and a few too
     * many candidates cost nothing when the ranker decides. A number matches whole.
     */
    static String toTsQuery(List<String> terms) {
        List<String> parts = new ArrayList<>();
        for (String term : terms) {
            String clean = term.replaceAll("[^\\p{L}\\p{N}]", "");
            if (clean.isEmpty()) {
                continue;
            }
            if (clean.chars().allMatch(Character::isDigit)) {
                parts.add(clean);
            } else {
                parts.add((clean.length() > 5 ? clean.substring(0, 5) : clean) + ":*");
            }
        }
        return String.join(" | ", parts);
    }

    /** pgvector's text form, {@code [0.1,0.2,…]}, at {@link #DIMENSIONS}; null when there is no usable vector. */
    static String toVectorLiteral(float[] vector) {
        if (vector == null || vector.length < DIMENSIONS) {
            return null;
        }
        double norm = 0;
        for (int i = 0; i < DIMENSIONS; i++) {
            norm += (double) vector[i] * vector[i];
        }
        norm = Math.sqrt(norm);
        if (norm == 0) {
            return null;
        }
        StringBuilder literal = new StringBuilder(DIMENSIONS * 10).append('[');
        for (int i = 0; i < DIMENSIONS; i++) {
            if (i > 0) {
                literal.append(',');
            }
            literal.append((float) (vector[i] / norm));
        }
        return literal.append(']').toString();
    }

    private static float[] fromVectorLiteral(String literal) {
        String body = literal.substring(literal.indexOf('[') + 1, literal.lastIndexOf(']'));
        String[] values = body.split(",");
        float[] vector = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            vector[i] = Float.parseFloat(values[i].trim());
        }
        return vector;
    }

    private CatalogMatch toMatch(ResultSet rs, int rowNum) throws SQLException {
        return new CatalogMatch(rs.getLong("source_id"), rs.getString("sku"), rs.getString("content"),
                fromJson(rs.getString("attributes")), rs.getDouble("score"));
    }

    private String toJson(Map<String, String> attributes) {
        try {
            return objectMapper.writeValueAsString(attributes == null ? Map.of() : attributes);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, String> fromJson(String json) {
        try {
            return json == null ? Map.of() : objectMapper.readValue(json, ATTRIBUTES);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
