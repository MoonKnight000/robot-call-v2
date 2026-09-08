package uz.murodjon.robotcallv2.mcp.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.murodjon.robotcallv2.mcp.infrastructure.persistence.entity.McpConnectionEntity;

import java.util.List;
import java.util.Optional;

public interface McpConnectionJpaRepository extends JpaRepository<McpConnectionEntity, Long> {

    List<McpConnectionEntity> findByCompanyIdOrderByNameAsc(Long companyId);

    Optional<McpConnectionEntity> findByCompanyIdAndId(Long companyId, Long id);

    Optional<McpConnectionEntity> findByCompanyIdAndName(Long companyId, String name);

    void deleteByCompanyIdAndId(Long companyId, Long id);

    /** The connections attached to one agent, through the join table. */
    @Query(value = """
            SELECT c.* FROM mcp_connection c
              JOIN ai_agent_mcp_connection j ON j.mcp_connection_id = c.id
             WHERE j.ai_agent_id = :agentId AND c.company_id = :companyId
             ORDER BY c.name
            """, nativeQuery = true)
    List<McpConnectionEntity> findByAgentId(@Param("companyId") Long companyId, @Param("agentId") Long agentId);

    @Modifying
    @Query(value = "INSERT INTO ai_agent_mcp_connection (ai_agent_id, mcp_connection_id) "
            + "VALUES (:agentId, :connectionId) ON CONFLICT DO NOTHING", nativeQuery = true)
    void attachToAgent(@Param("agentId") Long agentId, @Param("connectionId") Long connectionId);

    @Modifying
    @Query(value = "DELETE FROM ai_agent_mcp_connection "
            + "WHERE ai_agent_id = :agentId AND mcp_connection_id = :connectionId", nativeQuery = true)
    void detachFromAgent(@Param("agentId") Long agentId, @Param("connectionId") Long connectionId);
}
