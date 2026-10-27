package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallEventEntity;

import java.time.Instant;
import java.util.List;

@Repository
public interface CallEventJpaRepository extends JpaRepository<CallEventEntity, Long> {

    List<CallEventEntity> findByCompanyIdAndCallAttemptIdOrderByAtAscIdAsc(long companyId, long callAttemptId);

    @Modifying @Transactional
    @Query(value = "DELETE FROM call_event WHERE id IN "
            + "(SELECT id FROM call_event WHERE at < :cutoff LIMIT :batchSize)", nativeQuery = true)
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
