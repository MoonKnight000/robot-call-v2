package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.callrecord.domain.enums.CallEventType;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallEventEntity;

import java.time.Instant;
import java.util.List;

@Repository
public interface CallEventJpaRepository extends JpaRepository<CallEventEntity, Long> {

    List<CallEventEntity> findByCompanyIdAndCallAttemptIdOrderByAtAscIdAsc(long companyId, long callAttemptId);

    List<CallEventEntity> findByCompanyIdAndChatSessionIdOrderByAtAscIdAsc(long companyId, long chatSessionId);

    /** Newest first, at most the page's size — reads idx_call_event_company (company_id, type, at). */
    List<CallEventEntity> findByCompanyIdAndTypeAndAtBetweenOrderByAtDesc(Long companyId, CallEventType type,
                                                                          Instant from, Instant to, Pageable pageable);

    @Modifying @Transactional
    @Query(value = "DELETE FROM call_event WHERE id IN "
            + "(SELECT id FROM call_event WHERE at < :cutoff LIMIT :batchSize)", nativeQuery = true)
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
