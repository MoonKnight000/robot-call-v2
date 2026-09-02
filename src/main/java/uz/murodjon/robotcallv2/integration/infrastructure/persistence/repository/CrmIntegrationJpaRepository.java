package uz.murodjon.robotcallv2.integration.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import uz.murodjon.robotcallv2.integration.infrastructure.persistence.entity.CrmIntegrationEntity;

import java.util.Optional;

@Repository
public interface CrmIntegrationJpaRepository extends JpaRepository<CrmIntegrationEntity, Long> {

    Optional<CrmIntegrationEntity> findByCompanyId(long companyId);
}
