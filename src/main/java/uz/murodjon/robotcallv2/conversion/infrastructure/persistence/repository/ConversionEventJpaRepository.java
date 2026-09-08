package uz.murodjon.robotcallv2.conversion.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.murodjon.robotcallv2.conversion.infrastructure.persistence.entity.ConversionEventEntity;

import java.util.Optional;

public interface ConversionEventJpaRepository extends JpaRepository<ConversionEventEntity, Long> {

    Optional<ConversionEventEntity> findByCompanyIdAndDedupeKey(Long companyId, String dedupeKey);
}
