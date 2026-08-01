package uz.murodjon.uysotvoice.audit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import uz.murodjon.uysotvoice.audit.dto.AuditFilter;
import uz.murodjon.uysotvoice.audit.dto.AuditRow;
import uz.murodjon.uysotvoice.audit.entity.AuditLog;
import uz.murodjon.uysotvoice.audit.repository.AuditJpaRepository;
import uz.murodjon.uysotvoice.company.service.CurrentCompany;

import java.time.Instant;
import java.util.List;

/**
 * Records the state-changing things the API was asked to do (PROJECT.md §11).
 *
 * <p>This API dials real subscribers, starts campaigns that dial thousands of them, and
 * writes permanent opt-outs. When a client complains that they were called after asking
 * not to be, "who started that campaign, and when" has to be answerable — the application
 * log rolls over and is not indexed by entity.
 *
 * <p>Records the authenticated principal, which under API-key auth is the key's role
 * rather than a person. That is as specific as the auth model allows; it still separates a
 * read-only key from an admin one and pins the time.
 *
 * <p>Best-effort by design: an audit write must never fail the operation it describes.
 * Failures are logged at WARN with the record inline, so the information survives even
 * when the table does not.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditJpaRepository jpa;
    private final CurrentCompany company;

    public AuditService(AuditJpaRepository jpa, CurrentCompany company) {
        this.jpa = jpa;
        this.company = company;
    }

    /**
     * Record one action.
     *
     * @param action   short verb-ish code, e.g. {@code CAMPAIGN_START}, {@code CALL_ORIGINATE}
     * @param entity   what it acted on ({@code campaign}, {@code target}, {@code call}); nullable
     * @param entityId that entity's id, as text — not every one is a bigint; nullable
     * @param detail   anything worth reading later; nullable
     */
    public void record(String action, String entity, String entityId, String detail) {
        String actor = currentActor();
        try {
            AuditLog e = new AuditLog();
            e.setActor(actor);
            e.setAction(action);
            e.setEntity(entity);
            e.setEntityId(entityId);
            e.setDetail(detail);
            e.setCreatedAt(Instant.now());
            e.setCompanyId(company.id());
            e.setIpAddress(currentIp());
            jpa.save(e);
        } catch (Exception e) {
            log.warn("Audit write failed ({} {} {} by {}: {}): {}",
                    action, entity, entityId, actor, detail, e.getMessage());
        }
    }

    /** Most recent entries first — what an incident review reads. Scoped to the current company. */
    public List<AuditRow> recent(AuditFilter filter) {
        try {
            return jpa.findByCompanyId(company.id(), filter.actor(), filter.action(), filter.entity(),
                            filter.pageable())
                    .stream()
                    .map(AuditService::toRow)
                    .toList();
        } catch (Exception e) {
            log.warn("Audit read failed: {}", e.getMessage());
            return List.of();
        }
    }

    public long count(AuditFilter filter) {
        try {
            return jpa.countByCompanyId(company.id(), filter.actor(), filter.action(), filter.entity());
        } catch (Exception e) {
            log.warn("Audit count failed: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * The authenticated principal, or {@code system} for work no request asked for (the
     * dialer's own scheduled actions).
     */
    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return "system";
        }
        String roles = auth.getAuthorities().stream()
                .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                .sorted()
                .reduce((a, b) -> a + "+" + b)
                .orElse("");
        return roles.isBlank() ? auth.getName() : auth.getName() + ":" + roles;
    }

    /**
     * The caller's remote address, or {@code null} when there is no HTTP request on this
     * thread (the dialer's own scheduled work runs outside a request). Every {@link
     * #record} call happens synchronously inside the controller method it describes, so
     * Spring's request-bound {@link RequestContextHolder} is populated whenever there is
     * one to read — no separate filter needs to stash it.
     */
    private static String currentIp() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return null;
        }
        return servletAttrs.getRequest().getRemoteAddr();
    }

    private static AuditRow toRow(AuditLog e) {
        return new AuditRow(e.getId(), e.getActor(), e.getAction(), e.getEntity(), e.getEntityId(),
                e.getDetail(), e.getCreatedAt(), e.getIpAddress());
    }
}
