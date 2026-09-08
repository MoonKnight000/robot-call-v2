package uz.murodjon.robotcallv2.tool.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.murodjon.robotcallv2.tool.infrastructure.persistence.entity.AiAgentToolEntity;
import uz.murodjon.robotcallv2.tool.infrastructure.persistence.entity.AiAgentToolId;

public interface AiAgentToolJpaRepository extends JpaRepository<AiAgentToolEntity, AiAgentToolId> {

    @Query("SELECT COUNT(link) FROM AiAgentToolEntity link, ToolEntity tool "
            + "WHERE link.id.toolId = tool.id AND tool.id = :toolId AND tool.company.id = :companyId")
    long countAgentsUsingTool(@Param("companyId") long companyId, @Param("toolId") long toolId);

    @Modifying
    @Query("DELETE FROM AiAgentToolEntity link WHERE link.id.aiAgentId = :agentId AND link.id.toolId IN "
            + "(SELECT tool.id FROM ToolEntity tool WHERE tool.id = :toolId AND tool.company.id = :companyId)")
    void deleteAgentTool(@Param("companyId") long companyId,
                         @Param("agentId") long agentId,
                         @Param("toolId") long toolId);
}
