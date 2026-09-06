package uz.murodjon.robotcallv2.audit.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import uz.murodjon.robotcallv2.audit.application.dto.AuditFilter;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.audit.application.port.output.AuditLogRepository;
import uz.murodjon.robotcallv2.audit.domain.entity.AuditLog;
import uz.murodjon.robotcallv2.auth.application.dto.AuthenticatedUser;
import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;

import java.util.List;

/**
 * Records state-changing actions (PROJECT.md §11).
 */
@Service
public class AuditService implements AuditUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;
    private final CurrentCompany company;

    public AuditService(AuditLogRepository repository, CurrentCompany company) {
        this.repository = repository;
        this.company = company;
    }

    @Override
    public void record(String action, String entity, String entityId, String detail) {
        String actor = currentActor();
        try {
            repository.save(company.id(), AuditLog.entry(actor, action, entity, entityId, detail, currentIp()));
        } catch (Exception e) {
            log.warn("Audit write failed ({} {} {} by {}: {}): {}",
                    action, entity, entityId, actor, detail, e.getMessage());
        }
    }

    @Override
    public List<AuditLog> recent(AuditFilter filter) {
        try {
            return repository.findByCompanyId(company.id(), filter.actor(), filter.action(), filter.entity(),
                    filter.pageable());
        } catch (Exception e) {
            log.warn("Audit read failed: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public long count(AuditFilter filter) {
        try {
            return repository.countByCompanyId(company.id(), filter.actor(), filter.action(), filter.entity());
        } catch (Exception e) {
            log.warn("Audit count failed: {}", e.getMessage());
            return 0;
        }
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return "system";
        }
        if (auth.getPrincipal() instanceof AuthenticatedUser user) {
            String identifier = user.email() != null && !user.email().isBlank() ? user.email() : ("user:" + user.userId());
            return user.roleCode() != null ? identifier + " (" + user.roleCode() + ")" : identifier;
        }
        String name = auth.getName();
        if (name == null || name.isBlank() || name.startsWith("AuthenticatedUser[")) {
            return "system";
        }
        return name;
    }

    private static String currentIp() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return null;
        }
        return servletAttrs.getRequest().getRemoteAddr();
    }
}
