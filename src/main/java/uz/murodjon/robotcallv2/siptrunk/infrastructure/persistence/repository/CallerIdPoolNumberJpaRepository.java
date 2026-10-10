package uz.murodjon.robotcallv2.siptrunk.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import uz.murodjon.robotcallv2.siptrunk.infrastructure.persistence.entity.CallerIdPoolNumberEntity;

import java.time.Instant;
import java.util.Optional;

public interface CallerIdPoolNumberJpaRepository extends JpaRepository<CallerIdPoolNumberEntity, Long> {

    Optional<CallerIdPoolNumberEntity> findByIdAndPoolId(Long id, Long poolId);

    Optional<CallerIdPoolNumberEntity> findByPoolIdAndPhone(Long poolId, String phone);

    @Modifying
    @Query("UPDATE CallerIdPoolNumberEntity n SET n.usageCount = n.usageCount + 1, n.lastUsedAt = :usedAt WHERE n.id = :id")
    void incrementUsage(Long id, Instant usedAt);

    @Modifying
    @Query("UPDATE CallerIdPoolNumberEntity n SET n.answerCount = n.answerCount + 1 WHERE n.poolId = :poolId AND n.phone = :phone")
    void incrementAnswer(Long poolId, String phone);
}
