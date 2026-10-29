package uz.murodjon.robotcallv2.crm.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.crm.infrastructure.persistence.entity.CrmSettingsEntity;

import java.util.Optional;

@Repository
public interface CrmSettingsJpaRepository extends JpaRepository<CrmSettingsEntity, Long> {

    Optional<CrmSettingsEntity> findByCompanyId(long companyId);
}
