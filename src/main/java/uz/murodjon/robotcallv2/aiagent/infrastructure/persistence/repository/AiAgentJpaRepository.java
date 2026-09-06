package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AiAgentEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AiAgentJpaRepository extends JpaRepository<AiAgentEntity, Long>,
        JpaSpecificationExecutor<AiAgentEntity> {

    Optional<AiAgentEntity> findByIdAndCompanyId(long id, long companyId);

    List<AiAgentEntity> findByIdIn(Collection<Long> ids);

    /**
     * Native, because campaign and inbound_route belong to other features and this
     * repository is not the place to hold their JPA entities — it only needs to know
     * whether anything still points here before an agent is deleted.
     */
    @Query(value = "SELECT count(*) FROM campaign WHERE ai_agent_id = :id", nativeQuery = true)
    long countCampaignsUsing(@Param("id") long id);

    @Query(value = "SELECT count(*) FROM inbound_route WHERE ai_agent_id = :id", nativeQuery = true)
    long countInboundRoutesUsing(@Param("id") long id);
}
