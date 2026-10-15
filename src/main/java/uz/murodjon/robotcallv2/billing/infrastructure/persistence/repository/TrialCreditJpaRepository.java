package uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.TrialCreditEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TrialCreditJpaRepository extends JpaRepository<TrialCreditEntity, Long> {

    Optional<TrialCreditEntity> findByCompanyId(Long companyId);

    boolean existsByCompanyId(Long companyId);

    List<TrialCreditEntity> findBySettledAtIsNullAndExpiresAtBefore(Instant now);

    /** Conditional on {@code settled_at IS NULL}, so of two racing sweeps only one gets 1. */
    @Modifying
    @Query(value = """
            UPDATE billing_trial
               SET settled_at = :settledAt, clawback_uzs = :clawback
             WHERE company_id = :companyId AND settled_at IS NULL
            """, nativeQuery = true)
    int settle(@Param("companyId") Long companyId, @Param("clawback") Long clawback,
               @Param("settledAt") Instant settledAt);
}
