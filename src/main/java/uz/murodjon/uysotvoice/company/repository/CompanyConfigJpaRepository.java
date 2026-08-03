package uz.murodjon.uysotvoice.company.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.company.entity.CompanyConfigEntity;

import java.util.Optional;

/** Spring Data repository for {@link CompanyConfigEntity}. */
@Repository
public interface CompanyConfigJpaRepository extends JpaRepository<CompanyConfigEntity, Long> {

    Optional<CompanyConfigEntity> findByCompanyId(long companyId);
}
