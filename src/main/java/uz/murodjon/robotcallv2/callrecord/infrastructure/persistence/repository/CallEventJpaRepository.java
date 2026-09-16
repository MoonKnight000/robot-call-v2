package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallEventEntity;

import java.util.List;

@Repository
public interface CallEventJpaRepository extends JpaRepository<CallEventEntity, Long> {

    List<CallEventEntity> findByCompanyIdAndCallAttemptIdOrderByAtAscIdAsc(long companyId, long callAttemptId);
}
