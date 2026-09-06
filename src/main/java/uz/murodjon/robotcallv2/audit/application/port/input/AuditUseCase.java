package uz.murodjon.robotcallv2.audit.application.port.input;

import uz.murodjon.robotcallv2.audit.domain.entity.AuditFilter;
import uz.murodjon.robotcallv2.audit.domain.entity.AuditLog;

import java.util.List;

public interface AuditUseCase {

    void record(long companyId, String action, String entity, String entityId, String detail);

    List<AuditLog> recent(long companyId, AuditFilter filter);

    long count(long companyId, AuditFilter filter);
}
