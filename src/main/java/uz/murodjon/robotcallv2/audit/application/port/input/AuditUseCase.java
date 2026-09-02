package uz.murodjon.robotcallv2.audit.application.port.input;

import uz.murodjon.robotcallv2.audit.application.dto.AuditFilter;
import uz.murodjon.robotcallv2.audit.domain.entity.AuditLog;

import java.util.List;

public interface AuditUseCase {

    void record(String action, String entity, String entityId, String detail);

    List<AuditLog> recent(AuditFilter filter);

    long count(AuditFilter filter);
}
