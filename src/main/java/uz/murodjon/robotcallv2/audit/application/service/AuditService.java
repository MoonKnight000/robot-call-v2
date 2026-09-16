package uz.murodjon.robotcallv2.audit.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.audit.application.port.output.AuditLogRepository;
import uz.murodjon.robotcallv2.audit.domain.entity.AuditFilter;
import uz.murodjon.robotcallv2.audit.domain.entity.AuditLog;
import uz.murodjon.robotcallv2.security.CurrentUser;

import java.time.Instant;
import java.util.List;

/**
 * Records state-changing actions (PROJECT.md §11).
 */
@Service
public class AuditService implements AuditUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;
    /** Who did it and from where — "system" and no address on a thread with no request. */
    private final CurrentUser currentUser;

    public AuditService(AuditLogRepository repository, CurrentUser currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    @Override
    public void record(long companyId, String action, String entity, String entityId, String detail) {
        String actor = currentUser.actorName();
        try {
            repository.save(companyId, AuditLog.entry(actor, action, entity, entityId, detail,
                    currentUser.remoteAddress().orElse(null)));
        } catch (Exception e) {
            log.warn("Audit write failed ({} {} {} by {}: {}): {}",
                    action, entity, entityId, actor, detail, e.getMessage());
        }
    }

    @Override
    public List<AuditLog> findRecent(long companyId, AuditFilter filter) {
        try {
            return repository.findByCompanyId(companyId, filter);
        } catch (Exception e) {
            log.warn("Audit read failed: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public long count(long companyId, AuditFilter filter) {
        try {
            return repository.countByCompanyId(companyId, filter);
        } catch (Exception e) {
            log.warn("Audit count failed: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public int deleteCreatedBefore(Instant cutoff, int batchSize) {
        return repository.deleteCreatedBefore(cutoff, batchSize);
    }
}
