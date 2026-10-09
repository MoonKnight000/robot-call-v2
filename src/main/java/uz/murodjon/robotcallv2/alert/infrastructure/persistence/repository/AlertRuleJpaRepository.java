package uz.murodjon.robotcallv2.alert.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.alert.infrastructure.persistence.entity.AlertRuleEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AlertRuleJpaRepository extends JpaRepository<AlertRuleEntity, Long>,
        JpaSpecificationExecutor<AlertRuleEntity> {

    Optional<AlertRuleEntity> findByCompanyIdAndId(Long companyId, Long id);

    long countByCompanyId(Long companyId);

    List<AlertRuleEntity> findByEnabledTrueOrderById();

    @Modifying
    @Transactional
    void deleteByCompanyIdAndId(Long companyId, Long id);

    /** Written by the scheduler only; a settings save leaves these columns alone (see {@link AlertRuleEntity}). */
    @Modifying
    @Transactional
    @Query(value = "UPDATE alert_rule SET last_value = CAST(:value AS double precision), "
            + "last_evaluated_at = CAST(:evaluatedAt AS timestamptz), "
            + "last_triggered_at = CASE WHEN :triggered THEN CAST(:evaluatedAt AS timestamptz) ELSE last_triggered_at END "
            + "WHERE id = :id", nativeQuery = true)
    void recordEvaluation(@Param("id") long id, @Param("value") Double value,
                          @Param("evaluatedAt") Instant evaluatedAt, @Param("triggered") boolean triggered);
}
