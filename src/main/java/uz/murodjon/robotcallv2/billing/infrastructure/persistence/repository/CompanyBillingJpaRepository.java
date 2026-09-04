package uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.CompanyBillingEntity;

import java.util.Optional;

public interface CompanyBillingJpaRepository extends JpaRepository<CompanyBillingEntity, Long> {

    Optional<CompanyBillingEntity> findByCompanyId(Long companyId);

    @Modifying
    @Query("UPDATE CompanyBillingEntity b SET b.balanceUzs = b.balanceUzs + :amount, b.updatedAt = CURRENT_TIMESTAMP WHERE b.companyId = :companyId")
    void addBalance(@Param("companyId") Long companyId, @Param("amount") Long amount);
}
