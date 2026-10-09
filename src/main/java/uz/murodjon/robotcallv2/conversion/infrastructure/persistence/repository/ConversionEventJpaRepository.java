package uz.murodjon.robotcallv2.conversion.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.murodjon.robotcallv2.conversion.infrastructure.persistence.entity.ConversionEventEntity;

import java.time.Instant;
import java.util.Optional;

public interface ConversionEventJpaRepository extends JpaRepository<ConversionEventEntity, Long> {

    Optional<ConversionEventEntity> findByCompanyIdAndDedupeKey(Long companyId, String dedupeKey);

    boolean existsByCompanyIdAndPhoneAndOccurredAtGreaterThanEqual(Long companyId, String phone, Instant since);

    boolean existsByCompanyIdAndPhoneAndGoalKeyAndOccurredAtGreaterThanEqual(Long companyId, String phone,
                                                                          String goalKey, Instant since);
}
