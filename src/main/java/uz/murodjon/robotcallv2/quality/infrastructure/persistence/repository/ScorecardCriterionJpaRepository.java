package uz.murodjon.robotcallv2.quality.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.quality.infrastructure.persistence.entity.ScorecardCriterionEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScorecardCriterionJpaRepository extends JpaRepository<ScorecardCriterionEntity, Long> {

    Optional<ScorecardCriterionEntity> findByIdAndCompanyId(long id, long companyId);

    List<ScorecardCriterionEntity> findByCompanyId(long companyId, Pageable pageable);

    List<ScorecardCriterionEntity> findByCompanyIdAndEnabled(long companyId, boolean enabled, Pageable pageable);

    List<ScorecardCriterionEntity> findByCompanyIdAndEnabledTrueOrderById(long companyId);

    long countByCompanyId(long companyId);

    long countByCompanyIdAndEnabled(long companyId, boolean enabled);
}
