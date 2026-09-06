package uz.murodjon.robotcallv2.audit.application.port.output;

import uz.murodjon.robotcallv2.audit.domain.entity.AuditFilter;
import uz.murodjon.robotcallv2.audit.domain.entity.AuditLog;

import java.util.List;

public interface AuditLogRepository {

    AuditLog save(long companyId, AuditLog log);

    List<AuditLog> findByCompanyId(long companyId, AuditFilter filter);

    long countByCompanyId(long companyId, AuditFilter filter);
}
