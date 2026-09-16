package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AgentTestCaseEntity;

import java.util.List;
import java.util.Optional;

public interface AgentTestCaseJpaRepository extends JpaRepository<AgentTestCaseEntity, Long> {

    List<AgentTestCaseEntity> findByCompanyIdAndAgentIdOrderById(long companyId, long agentId);

    Optional<AgentTestCaseEntity> findByIdAndCompanyIdAndAgentId(long id, long companyId, long agentId);

    Optional<AgentTestCaseEntity> findByIdAndCompanyId(long id, long companyId);
}
