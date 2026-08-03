package uz.murodjon.uysotvoice.integration.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.integration.entity.CrmIntegrationEntity;

import java.util.Optional;

/** Spring Data repository for {@link CrmIntegrationEntity}. */
@Repository
public interface CrmIntegrationJpaRepository extends JpaRepository<CrmIntegrationEntity, Long> {

    Optional<CrmIntegrationEntity> findByCompanyId(long companyId);
}
