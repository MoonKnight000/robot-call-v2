package uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.entity.CallQueueEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface CallQueueJpaRepository extends JpaRepository<CallQueueEntity, Long> {

    Optional<CallQueueEntity> findByIdAndCompanyId(long id, long companyId);

    List<CallQueueEntity> findByCompanyId(long companyId, Pageable pageable);

    long countByCompanyId(long companyId);

    List<CallQueueEntity> findByCompanyIdAndEnabledTrue(long companyId);
}
