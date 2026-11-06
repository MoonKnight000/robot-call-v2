package uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.adapter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.KnowledgeChunkRepository;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeChunk;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgePassage;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeSourcePage;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.util.LikePatterns;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Passages in {@code knowledge_chunk} (V52): the HNSW index answers the nearest passages, the text index the
 * passages that share the caller's words.
 *
 * <p>Plain JDBC rather than an entity, like {@link CatalogItemRepositoryAdapter}: the embedding is a pgvector
 * column, which Hibernate would need an extra module to map, and a passage is never loaded to be changed —
 * a source's passages are replaced whole and read as search results. Vectors are cut to
 * {@link CatalogItemRepositoryAdapter#DIMENSIONS} and normalised the same way.
 */
@Component
public class KnowledgeChunkRepositoryAdapter implements KnowledgeChunkRepository {

    private static final int INSERT_BATCH = 500;

    /**
     * Every search's narrowing: this company's passages of sources that did not fail, that this agent may use.
     * A failed source has no passages anyway; one being re-indexed keeps answering with its current text.
     */
    private static final String VISIBLE = """
             FROM knowledge_chunk kc
             JOIN knowledge_source s ON s.id = kc.source_id
            WHERE kc.company_id = :companyId
              AND s.status <> 'FAILED'
              AND s.enabled
              AND NOT kc.disabled
              AND (CAST(:agentId AS BIGINT) IS NULL
                   OR (s.collection_id IS NULL AND (s.agent_id IS NULL OR s.agent_id = :agentId))
                   OR s.collection_id IN (SELECT k.collection_id FROM ai_agent_knowledge_collection k
                                           WHERE k.agent_id = :agentId)
                   OR s.id IN (:extraSourceIds))
            """;

    private static final String CHUNK_COLUMNS = "id, source_id, ordinal, content, content_hash, page_url, disabled, original_hash";

    private static final String PASSAGE_COLUMNS = "SELECT kc.id, kc.source_id, s.name AS source_name, kc.content, kc.page_url, ";

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;

    public KnowledgeChunkRepositoryAdapter(JdbcClient jdbcClient, JdbcTemplate jdbcTemplate) {
        this.jdbcClient = jdbcClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void replaceBySourceId(long companyId, long sourceId, List<KnowledgeChunk> chunks) {
        jdbcTemplate.update("DELETE FROM knowledge_chunk WHERE source_id = ?", sourceId);
        for (int from = 0; from < chunks.size(); from += INSERT_BATCH) {
            List<KnowledgeChunk> batch = chunks.subList(from, Math.min(from + INSERT_BATCH, chunks.size()));
            jdbcTemplate.batchUpdate("""
                    INSERT INTO knowledge_chunk (source_id, company_id, ordinal, content, content_hash, page_url, embedding,
                                                 disabled, original_hash)
                    VALUES (?, ?, ?, ?, ?, ?, CAST(? AS vector), ?, ?)
                    """, batch, batch.size(), (statement, chunk) -> {
                statement.setLong(1, sourceId);
                statement.setLong(2, companyId);
                statement.setInt(3, chunk.ordinal());
                statement.setString(4, chunk.content());
                statement.setString(5, chunk.contentHash());
                statement.setString(6, chunk.pageUrl());
                statement.setString(7, CatalogItemRepositoryAdapter.toVectorLiteral(chunk.embedding()));
                statement.setBoolean(8, chunk.disabled());
                statement.setString(9, chunk.originalHash());
            });
        }
    }

    @Override
    public void deleteBySourceId(long sourceId) {
        jdbcTemplate.update("DELETE FROM knowledge_chunk WHERE source_id = ?", sourceId);
    }

    @Override
    public void deleteUncuratedBySourceId(long sourceId) {
        jdbcTemplate.update("DELETE FROM knowledge_chunk WHERE source_id = ? AND NOT disabled AND original_hash IS NULL",
                sourceId);
    }

    @Override
    public Map<String, float[]> findEmbeddingsBySourceId(long sourceId) {
        Map<String, float[]> vectors = new HashMap<>();
        jdbcClient.sql("SELECT content_hash, embedding::text AS embedding FROM knowledge_chunk "
                        + "WHERE source_id = :sourceId AND embedding IS NOT NULL")
                .param("sourceId", sourceId)
                .query((rs, rowNum) -> Map.entry(rs.getString("content_hash"),
                        CatalogItemRepositoryAdapter.fromVectorLiteral(rs.getString("embedding"))))
                .list()
                .forEach(entry -> vectors.putIfAbsent(entry.getKey(), entry.getValue()));
        return vectors;
    }

    /**
     * In a transaction for the one setting it needs: a company is a slice of one shared index, and without an
     * iterative scan the index hands back its nearest rows across every company and a small knowledge base comes
     * back short once the filter drops the others (pgvector 0.8+).
     */
    @Override
    @Transactional(readOnly = true)
    public List<KnowledgePassage> findNearest(long companyId, Long agentId, List<Long> extraSourceIds, float[] query,
                                              int limit) {
        String vector = CatalogItemRepositoryAdapter.toVectorLiteral(query);
        if (vector == null || limit <= 0) {
            return List.of();
        }
        jdbcClient.sql("SET LOCAL hnsw.iterative_scan = relaxed_order").update();
        return jdbcClient.sql(PASSAGE_COLUMNS + "1 - (kc.embedding <=> CAST(:query AS vector)) AS score" + VISIBLE
                        + " AND kc.embedding IS NOT NULL ORDER BY kc.embedding <=> CAST(:query AS vector) LIMIT :limit")
                .param("companyId", companyId)
                .param("agentId", agentId, Types.BIGINT)
                .param("extraSourceIds", orNone(extraSourceIds))
                .param("query", vector)
                .param("limit", limit)
                .query(this::toPassage)
                .list();
    }

    @Override
    public List<KnowledgePassage> findByTerms(long companyId, Long agentId, List<Long> extraSourceIds,
                                              List<String> terms, int limit) {
        String tsQuery = CatalogItemRepositoryAdapter.toTsQuery(terms);
        if (tsQuery.isEmpty() || limit <= 0) {
            return List.of();
        }
        return jdbcClient.sql(PASSAGE_COLUMNS + "0.0 AS score" + VISIBLE
                        + " AND kc.search_text @@ to_tsquery('simple', :terms)"
                        + " ORDER BY ts_rank(kc.search_text, to_tsquery('simple', :terms)) DESC LIMIT :limit")
                .param("companyId", companyId)
                .param("agentId", agentId, Types.BIGINT)
                .param("extraSourceIds", orNone(extraSourceIds))
                .param("terms", tsQuery)
                .param("limit", limit)
                .query(this::toPassage)
                .list();
    }

    @Override
    public List<KnowledgeChunk> findWithoutEmbedding(int limit) {
        return jdbcClient.sql("SELECT id, source_id, ordinal, content, content_hash, page_url FROM knowledge_chunk "
                        + "WHERE embedding IS NULL ORDER BY id LIMIT :limit")
                .param("limit", limit)
                .query((rs, rowNum) -> new KnowledgeChunk(rs.getLong("id"), rs.getLong("source_id"),
                        rs.getInt("ordinal"), rs.getString("content"), rs.getString("content_hash"),
                        rs.getString("page_url"), null, false, null))
                .list();
    }

    @Override
    public void updateEmbedding(long chunkId, float[] embedding) {
        jdbcTemplate.update("UPDATE knowledge_chunk SET embedding = CAST(? AS vector) WHERE id = ?",
                CatalogItemRepositoryAdapter.toVectorLiteral(embedding), chunkId);
    }

    @Override
    public List<KnowledgeChunk> findCuratedBySourceId(long sourceId) {
        return jdbcClient.sql("SELECT " + CHUNK_COLUMNS + ", embedding::text AS embedding FROM knowledge_chunk "
                        + "WHERE source_id = :sourceId AND (disabled OR original_hash IS NOT NULL) ORDER BY ordinal")
                .param("sourceId", sourceId)
                .query((rs, rowNum) -> toChunk(rs, true))
                .list();
    }

    @Override
    public PageableData<KnowledgeChunk> findPageBySourceId(long sourceId, String search, String pageUrl, int page,
                                                           int size) {
        String pattern = search == null || search.isBlank()
                ? null : LikePatterns.buildContainsPattern(search.toLowerCase(Locale.ROOT));
        String where = " FROM knowledge_chunk WHERE source_id = :sourceId"
                + (pattern != null ? " AND lower(content) LIKE :pattern ESCAPE '" + LikePatterns.ESCAPE + "'" : "")
                + (pageUrl != null && !pageUrl.isBlank() ? " AND page_url = :pageUrl" : "");
        Map<String, Object> params = new HashMap<>();
        params.put("sourceId", sourceId);
        if (pattern != null) {
            params.put("pattern", pattern);
        }
        if (pageUrl != null && !pageUrl.isBlank()) {
            params.put("pageUrl", pageUrl);
        }
        long total = jdbcClient.sql("SELECT count(*)" + where).params(params).query(Long.class).single();
        List<KnowledgeChunk> rows = jdbcClient.sql("SELECT " + CHUNK_COLUMNS + where
                        + " ORDER BY ordinal LIMIT :limit OFFSET :offset")
                .params(params)
                .param("limit", size)
                .param("offset", (long) page * size)
                .query((rs, rowNum) -> toChunk(rs, false))
                .list();
        return PageableData.of(rows, page, size, total);
    }

    @Override
    public List<KnowledgeSourcePage> findPagesBySourceId(long sourceId) {
        return jdbcClient.sql("""
                        SELECT page_url, count(*) AS chunks, count(*) FILTER (WHERE disabled) AS disabled_chunks
                          FROM knowledge_chunk
                         WHERE source_id = :sourceId AND page_url IS NOT NULL
                         GROUP BY page_url
                         ORDER BY min(ordinal)
                        """)
                .param("sourceId", sourceId)
                .query((rs, rowNum) -> new KnowledgeSourcePage(rs.getString("page_url"), rs.getInt("chunks"),
                        rs.getInt("disabled_chunks")))
                .list();
    }

    @Override
    public KnowledgeChunk findBySourceIdAndId(long sourceId, long chunkId) {
        return jdbcClient.sql("SELECT " + CHUNK_COLUMNS + " FROM knowledge_chunk WHERE source_id = :sourceId AND id = :id")
                .param("sourceId", sourceId)
                .param("id", chunkId)
                .query((rs, rowNum) -> toChunk(rs, false))
                .optional()
                .orElse(null);
    }

    @Override
    public void updateCuration(KnowledgeChunk chunk) {
        // The vector is replaced only with the text: a passage merely switched off or on keeps the one it has.
        jdbcTemplate.update("""
                        UPDATE knowledge_chunk
                           SET embedding = CASE WHEN content_hash = ? THEN embedding ELSE CAST(? AS vector) END,
                               content = ?, content_hash = ?, disabled = ?, original_hash = ?
                         WHERE id = ?
                        """,
                chunk.contentHash(), CatalogItemRepositoryAdapter.toVectorLiteral(chunk.embedding()),
                chunk.content(), chunk.contentHash(), chunk.disabled(), chunk.originalHash(), chunk.id());
    }

    private static KnowledgeChunk toChunk(ResultSet rs, boolean withEmbedding) throws SQLException {
        String embedding = withEmbedding ? rs.getString("embedding") : null;
        return new KnowledgeChunk(rs.getLong("id"), rs.getLong("source_id"), rs.getInt("ordinal"),
                rs.getString("content"), rs.getString("content_hash"), rs.getString("page_url"),
                embedding != null ? CatalogItemRepositoryAdapter.fromVectorLiteral(embedding) : null,
                rs.getBoolean("disabled"), rs.getString("original_hash"));
    }

    /** {@code IN ()} is not SQL; an id no row has stands for "no extra sources". */
    private static List<Long> orNone(List<Long> ids) {
        return ids == null || ids.isEmpty() ? List.of(-1L) : ids;
    }

    private KnowledgePassage toPassage(ResultSet rs, int rowNum) throws SQLException {
        return new KnowledgePassage(rs.getLong("id"), rs.getLong("source_id"), rs.getString("source_name"), rs.getString("content"),
                rs.getDouble("score"), rs.getString("page_url"));
    }
}
