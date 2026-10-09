package uz.murodjon.robotcallv2.copilot.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.copilot.infrastructure.persistence.entity.CopilotSessionEntity;

import java.util.Optional;

public interface CopilotSessionJpaRepository extends JpaRepository<CopilotSessionEntity, Long>,
        JpaSpecificationExecutor<CopilotSessionEntity> {

    Optional<CopilotSessionEntity> findByCompanyIdAndUserIdAndId(Long companyId, Long userId, Long id);

    @Modifying
    @Transactional
    void deleteByCompanyIdAndUserIdAndId(Long companyId, Long userId, Long id);

    @Modifying
    @Transactional
    @Query(value = "UPDATE copilot_session SET updated_at = now() WHERE id = :id", nativeQuery = true)
    void touch(@Param("id") long id);
}
