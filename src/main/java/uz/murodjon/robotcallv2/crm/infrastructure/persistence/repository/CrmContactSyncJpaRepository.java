package uz.murodjon.robotcallv2.crm.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.crm.infrastructure.persistence.entity.CrmContactSyncEntity;

import java.time.Instant;
import java.util.List;

@Repository
public interface CrmContactSyncJpaRepository extends JpaRepository<CrmContactSyncEntity, Long> {

    @Query("SELECT s.companyId FROM CrmContactSyncEntity s "
            + "WHERE s.enabled = true AND s.nextRunAt IS NOT NULL AND s.nextRunAt <= :now ORDER BY s.nextRunAt")
    List<Long> findDueCompanyIds(@Param("now") Instant now, Pageable pageable);
}
