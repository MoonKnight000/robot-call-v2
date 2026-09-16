package uz.murodjon.robotcallv2.insight.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import uz.murodjon.robotcallv2.insight.domain.enums.InsightStatus;
import uz.murodjon.robotcallv2.insight.domain.enums.InsightType;
import uz.murodjon.robotcallv2.insight.infrastructure.persistence.entity.AgentInsightEntity;

import java.util.Optional;

public interface AgentInsightJpaRepository extends JpaRepository<AgentInsightEntity, Long>,
        JpaSpecificationExecutor<AgentInsightEntity> {

    Optional<AgentInsightEntity> findByCompanyIdAndId(Long companyId, Long id);

    boolean existsByAiAgentIdAndTypeAndTitleAndStatus(Long aiAgentId, InsightType type, String title,
                                                      InsightStatus status);

    Optional<AgentInsightEntity> findByEvalRunId(Long evalRunId);
}
