package uz.murodjon.uysotvoice.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
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

    private static final RowMapper<AuditRow> MAPPER = (rs, i) -> new AuditRow(
            rs.getLong("id"),
            rs.getString("actor"),
            rs.getString("action"),
            rs.getString("entity"),
            rs.getString("entity_id"),
            rs.getString("detail"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbc;

    public AuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
            jdbc.update("INSERT INTO audit_log(actor, action, entity, entity_id, detail, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    actor, action, entity, entityId, detail, Timestamp.from(Instant.now()));
        } catch (Exception e) {
            log.warn("Audit write failed ({} {} {} by {}: {}): {}",
                    action, entity, entityId, actor, detail, e.getMessage());
        }
    }

    /** Most recent entries first — what an incident review reads. */
    public List<AuditRow> recent(int limit) {
        int capped = Math.min(Math.max(1, limit), 500);
        try {
            return jdbc.query("SELECT * FROM audit_log ORDER BY id DESC LIMIT ?", MAPPER, capped);
        } catch (Exception e) {
            log.warn("Audit read failed: {}", e.getMessage());
            return List.of();
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
}
