package uz.murodjon.uysotvoice.audit.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.audit.entity.AuditLog;

import java.util.List;

/** Spring Data repository for {@link AuditLog}. */
@Repository
public interface AuditJpaRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByCompanyId(long companyId, Pageable pageable);

    long countByCompanyId(long companyId);
}
