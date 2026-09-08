package uz.murodjon.robotcallv2.conversion.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.murodjon.robotcallv2.conversion.infrastructure.persistence.entity.ConversionGoalEntity;

import java.util.List;
import java.util.Optional;

public interface ConversionGoalJpaRepository extends JpaRepository<ConversionGoalEntity, Long> {

    List<ConversionGoalEntity> findByCompanyIdOrderByGoalKeyAsc(Long companyId);

    Optional<ConversionGoalEntity> findByCompanyIdAndGoalKey(Long companyId, String goalKey);

    Optional<ConversionGoalEntity> findByCompanyIdAndId(Long companyId, Long id);
}
