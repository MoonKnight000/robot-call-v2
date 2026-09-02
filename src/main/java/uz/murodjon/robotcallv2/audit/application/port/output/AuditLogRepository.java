package uz.murodjon.robotcallv2.audit.application.port.output;

import org.springframework.data.domain.Pageable;
import uz.murodjon.robotcallv2.audit.domain.entity.AuditLog;

import java.util.List;

public interface AuditLogRepository {

    AuditLog save(long companyId, AuditLog log);

    List<AuditLog> findByCompanyId(long companyId, String actor, String action, String entity, Pageable pageable);

    long countByCompanyId(long companyId, String actor, String action, String entity);
}
