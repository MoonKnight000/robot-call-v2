package uz.murodjon.robotcallv2.audit.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import uz.murodjon.robotcallv2.audit.infrastructure.persistence.entity.AuditLogEntity;

import java.util.List;

@Repository
public interface AuditLogJpaRepository extends JpaRepository<AuditLogEntity, Long>, JpaSpecificationExecutor<AuditLogEntity> {

    @Query("SELECT a FROM AuditLogEntity a WHERE a.company.id = :companyId")
    List<AuditLogEntity> findByCompanyId(@Param("companyId") long companyId, Pageable pageable);

    @Query("SELECT count(a) FROM AuditLogEntity a WHERE a.company.id = :companyId")
    long countByCompanyId(@Param("companyId") long companyId);
}
