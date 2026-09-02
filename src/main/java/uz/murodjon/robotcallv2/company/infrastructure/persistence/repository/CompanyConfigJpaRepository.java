package uz.murodjon.robotcallv2.company.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyConfigEntity;

import java.util.Optional;

@Repository
public interface CompanyConfigJpaRepository extends JpaRepository<CompanyConfigEntity, Long> {

    @Query("SELECT cc FROM CompanyConfigEntity cc WHERE cc.company.id = :companyId")
    Optional<CompanyConfigEntity> findByCompanyId(@Param("companyId") long companyId);
}
