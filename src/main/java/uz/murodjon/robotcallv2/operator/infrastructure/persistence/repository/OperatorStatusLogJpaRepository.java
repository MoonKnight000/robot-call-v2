package uz.murodjon.robotcallv2.operator.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.operator.infrastructure.persistence.entity.OperatorStatusLogEntity;

import java.time.Instant;
import java.util.List;

@Repository
public interface OperatorStatusLogJpaRepository extends JpaRepository<OperatorStatusLogEntity, Long> {

    @Modifying
    @Transactional
    @Query("UPDATE OperatorStatusLogEntity l SET l.endedAt = :at WHERE l.userId = :userId AND l.endedAt IS NULL")
    int closeOpen(@Param("userId") long userId, @Param("at") Instant at);

    List<OperatorStatusLogEntity> findByEndedAtIsNull();
}
