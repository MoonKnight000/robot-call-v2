package uz.murodjon.uysotvoice.audit.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.audit.entity.AuditLog;

import java.util.List;

/** Spring Data repository for {@link AuditLog}. */
@Repository
public interface AuditJpaRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByCompanyId(long companyId, Pageable pageable);

    long countByCompanyId(long companyId);

    @Query("SELECT a FROM AuditLog a WHERE a.companyId = :companyId "
            + "AND (:actor IS NULL OR a.actor = :actor) "
            + "AND (:action IS NULL OR a.action = :action) "
            + "AND (:entity IS NULL OR a.entity = :entity)")
    List<AuditLog> findByCompanyId(@Param("companyId") long companyId, @Param("actor") String actor,
                                   @Param("action") String action, @Param("entity") String entity,
                                   Pageable pageable);

    @Query("SELECT count(a) FROM AuditLog a WHERE a.companyId = :companyId "
            + "AND (:actor IS NULL OR a.actor = :actor) "
            + "AND (:action IS NULL OR a.action = :action) "
            + "AND (:entity IS NULL OR a.entity = :entity)")
    long countByCompanyId(@Param("companyId") long companyId, @Param("actor") String actor,
                          @Param("action") String action, @Param("entity") String entity);
}
