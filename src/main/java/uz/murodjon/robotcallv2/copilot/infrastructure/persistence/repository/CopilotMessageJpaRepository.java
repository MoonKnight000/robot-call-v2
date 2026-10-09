package uz.murodjon.robotcallv2.copilot.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.copilot.infrastructure.persistence.entity.CopilotMessageEntity;

import java.util.List;

public interface CopilotMessageJpaRepository extends JpaRepository<CopilotMessageEntity, Long> {

    List<CopilotMessageEntity> findBySessionIdOrderById(Long sessionId);

    @Modifying
    @Transactional
    @Query(value = "UPDATE copilot_message SET charged_uzs = :chargedUzs WHERE id = :id", nativeQuery = true)
    void updateChargedUzs(@Param("id") long id, @Param("chargedUzs") long chargedUzs);
}
