package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallTechnicalEntity;

/**
 * Spring Data repository for {@link CallTechnicalEntity}.
 */
@Repository
public interface CallTechnicalJpaRepository extends JpaRepository<CallTechnicalEntity, Long> {
}
