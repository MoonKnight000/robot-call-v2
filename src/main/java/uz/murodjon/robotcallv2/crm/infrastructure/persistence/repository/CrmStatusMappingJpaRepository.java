package uz.murodjon.robotcallv2.crm.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.crm.infrastructure.persistence.entity.CrmStatusMappingEntity;
import uz.murodjon.robotcallv2.crm.infrastructure.persistence.entity.CrmStatusMappingId;

import java.util.List;

@Repository
public interface CrmStatusMappingJpaRepository extends JpaRepository<CrmStatusMappingEntity, CrmStatusMappingId> {

    List<CrmStatusMappingEntity> findByIdCompanyId(Long companyId);

    void deleteByIdCompanyId(Long companyId);
}
