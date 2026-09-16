package uz.murodjon.robotcallv2.tool.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolExecutionStatus;
import uz.murodjon.robotcallv2.tool.infrastructure.persistence.entity.ToolExecutionEntity;

public interface ToolExecutionJpaRepository extends JpaRepository<ToolExecutionEntity, Long> {

    long countByCompanyIdAndCallAttemptIdAndStatusNot(Long companyId, Long callAttemptId, ToolExecutionStatus status);
}
