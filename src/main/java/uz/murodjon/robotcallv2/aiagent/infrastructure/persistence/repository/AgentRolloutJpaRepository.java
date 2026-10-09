package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AgentRolloutEntity;

import java.util.Optional;

public interface AgentRolloutJpaRepository extends JpaRepository<AgentRolloutEntity, Long> {

    Optional<AgentRolloutEntity> findByCompanyIdAndAgentId(Long companyId, Long agentId);

    @Modifying
    @Transactional
    void deleteByCompanyIdAndAgentId(Long companyId, Long agentId);
}
