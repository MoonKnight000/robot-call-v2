package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallTalkMetricsEntity;

/**
 * Spring Data repository for {@link CallTalkMetricsEntity}.
 */
@Repository
public interface CallTalkMetricsJpaRepository extends JpaRepository<CallTalkMetricsEntity, Long> {
}
