package uz.murodjon.robotcallv2.widget.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.murodjon.robotcallv2.widget.infrastructure.persistence.entity.AgentWidgetEntity;

import java.util.List;
import java.util.Optional;

public interface AgentWidgetJpaRepository extends JpaRepository<AgentWidgetEntity, Long> {

    List<AgentWidgetEntity> findByCompanyIdAndAgentIdOrderByCreatedAtDesc(long companyId, long agentId);

    Optional<AgentWidgetEntity> findByCompanyIdAndId(long companyId, long id);

    Optional<AgentWidgetEntity> findByWidgetKey(String widgetKey);

    void deleteByCompanyIdAndId(long companyId, long id);
}
