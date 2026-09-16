package uz.murodjon.robotcallv2.audit.application.port.output;

import uz.murodjon.robotcallv2.audit.domain.entity.AuditFilter;
import uz.murodjon.robotcallv2.audit.domain.entity.AuditLog;

import java.time.Instant;
import java.util.List;

public interface AuditLogRepository {

    AuditLog save(long companyId, AuditLog log);

    List<AuditLog> findByCompanyId(long companyId, AuditFilter filter);

    long countByCompanyId(long companyId, AuditFilter filter);

    /** Deletes up to {@code batchSize} rows, across every company, written before {@code cutoff}. */
    int deleteCreatedBefore(Instant cutoff, int batchSize);
}
