package uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.adapter;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.KnowledgeCollectionRepository;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeCollection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Collections in {@code knowledge_collection} and their agents in {@code ai_agent_knowledge_collection}
 * (V6). Plain JDBC: a collection is a name and a join table, nothing a managed entity would add.
 */
@Component
public class KnowledgeCollectionRepositoryAdapter implements KnowledgeCollectionRepository {

    private static final String COLUMNS = "id, company_id, name, created_at";

    private final JdbcClient jdbcClient;

    public KnowledgeCollectionRepositoryAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public KnowledgeCollection save(KnowledgeCollection collection) {
        if (collection.id() != 0) {
            jdbcClient.sql("UPDATE knowledge_collection SET name = :name WHERE id = :id AND company_id = :companyId")
                    .param("name", collection.name())
                    .param("id", collection.id())
                    .param("companyId", collection.companyId())
                    .update();
            return collection;
        }
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcClient.sql("INSERT INTO knowledge_collection (company_id, name) VALUES (:companyId, :name)")
                .param("companyId", collection.companyId())
                .param("name", collection.name())
                .update(keys, "id");
        long id = ((Number) keys.getKeys().get("id")).longValue();
        return findByCompanyIdAndId(collection.companyId(), id).orElseThrow();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KnowledgeCollection> findByCompanyIdAndId(long companyId, long id) {
        return jdbcClient.sql("SELECT " + COLUMNS + " FROM knowledge_collection WHERE id = :id AND company_id = :companyId")
                .param("id", id)
                .param("companyId", companyId)
                .query((rs, row) -> toCollection(rs))
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KnowledgeCollection> findByCompanyIdAndName(long companyId, String name) {
        return jdbcClient.sql("SELECT " + COLUMNS + " FROM knowledge_collection "
                        + "WHERE company_id = :companyId AND lower(name) = lower(:name)")
                .param("companyId", companyId)
                .param("name", name)
                .query((rs, row) -> toCollection(rs))
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<KnowledgeCollection> findByCompanyId(long companyId) {
        return jdbcClient.sql("SELECT " + COLUMNS + " FROM knowledge_collection WHERE company_id = :companyId ORDER BY name")
                .param("companyId", companyId)
                .query((rs, row) -> toCollection(rs))
                .list();
    }

    @Override
    @Transactional
    public void deleteByCompanyIdAndId(long companyId, long id) {
        jdbcClient.sql("DELETE FROM knowledge_collection WHERE id = :id AND company_id = :companyId")
                .param("id", id)
                .param("companyId", companyId)
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Integer> countSourcesByCompanyId(long companyId) {
        Map<Long, Integer> counts = new HashMap<>();
        jdbcClient.sql("SELECT collection_id, count(*) AS total FROM knowledge_source "
                        + "WHERE company_id = :companyId AND collection_id IS NOT NULL GROUP BY collection_id")
                .param("companyId", companyId)
                .query((rs, row) -> counts.put(rs.getLong("collection_id"), rs.getInt("total")))
                .list();
        return counts;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<Long>> findAgentIdsByCompanyId(long companyId) {
        Map<Long, List<Long>> agents = new LinkedHashMap<>();
        jdbcClient.sql("SELECT a.collection_id, a.agent_id FROM ai_agent_knowledge_collection a "
                        + "JOIN knowledge_collection c ON c.id = a.collection_id "
                        + "WHERE c.company_id = :companyId ORDER BY a.agent_id")
                .param("companyId", companyId)
                .query((rs, row) -> agents.computeIfAbsent(rs.getLong("collection_id"), key -> new ArrayList<>())
                        .add(rs.getLong("agent_id")))
                .list();
        return agents;
    }

    @Override
    @Transactional
    public void replaceAgents(long collectionId, Collection<Long> agentIds) {
        jdbcClient.sql("DELETE FROM ai_agent_knowledge_collection WHERE collection_id = :collectionId")
                .param("collectionId", collectionId)
                .update();
        for (Long agentId : new HashSet<>(agentIds)) {
            jdbcClient.sql("INSERT INTO ai_agent_knowledge_collection (agent_id, collection_id) VALUES (:agentId, :collectionId)")
                    .param("agentId", agentId)
                    .param("collectionId", collectionId)
                    .update();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> findCollectionIdsByAgentId(long agentId) {
        return new HashSet<>(jdbcClient.sql("SELECT collection_id FROM ai_agent_knowledge_collection WHERE agent_id = :agentId")
                .param("agentId", agentId)
                .query(Long.class)
                .list());
    }

    private static KnowledgeCollection toCollection(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new KnowledgeCollection(rs.getLong("id"), rs.getLong("company_id"), rs.getString("name"),
                rs.getTimestamp("created_at").toInstant());
    }
}
