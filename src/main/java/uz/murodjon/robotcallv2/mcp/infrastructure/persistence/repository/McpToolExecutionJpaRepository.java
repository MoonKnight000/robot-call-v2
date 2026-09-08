package uz.murodjon.robotcallv2.mcp.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.murodjon.robotcallv2.mcp.infrastructure.persistence.entity.McpToolExecutionEntity;

public interface McpToolExecutionJpaRepository extends JpaRepository<McpToolExecutionEntity, Long> {
}
