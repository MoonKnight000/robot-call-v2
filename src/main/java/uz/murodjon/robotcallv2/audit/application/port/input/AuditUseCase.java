package uz.murodjon.robotcallv2.audit.application.port.input;

import uz.murodjon.robotcallv2.audit.domain.entity.AuditFilter;
import uz.murodjon.robotcallv2.audit.domain.entity.AuditLog;

import java.time.Instant;
import java.util.List;

public interface AuditUseCase {

    void record(long companyId, String action, String entity, String entityId, String detail);

    List<AuditLog> findRecent(long companyId, AuditFilter filter);

    long count(long companyId, AuditFilter filter);

    /**
     * The retention sweep: deletes up to {@code batchSize} entries, across every company,
     * written before {@code cutoff}; returns how many went.
     */
    int deleteCreatedBefore(Instant cutoff, int batchSize);
}
