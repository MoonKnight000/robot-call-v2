package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AgentEvalResultEntity;

import java.util.List;

public interface AgentEvalResultJpaRepository extends JpaRepository<AgentEvalResultEntity, Long> {

    List<AgentEvalResultEntity> findByRunIdOrderById(long runId);
}
