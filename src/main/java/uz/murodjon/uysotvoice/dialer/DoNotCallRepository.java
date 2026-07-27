package uz.murodjon.uysotvoice.dialer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JdbcTemplate DAO for {@code do_not_call_list} — the phone-level opt-out list
 * (PROJECT.md §11.4). Keyed by phone rather than by target so an opt-out survives
 * into campaigns that do not exist yet.
 *
 * <p>{@link #add} is idempotent: a client who repeats the request on a second call
 * must not fail the write.
 */
@Repository
public class DoNotCallRepository {

    private static final Logger log = LoggerFactory.getLogger(DoNotCallRepository.class);

    private final JdbcTemplate jdbc;

    public DoNotCallRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Record an opt-out for {@code phone}. Best-effort: a DB failure is logged, never
     * thrown — this runs during call teardown and must not break it.
     *
     * @param source where the opt-out came from: {@code CALL}, {@code MANUAL}, {@code IMPORT}
     */
    public void add(String phone, String reason, String source) {
        if (phone == null || phone.isBlank()) {
            return;
        }
        try {
            jdbc.update("INSERT INTO do_not_call_list(phone, reason, source) VALUES (?, ?, ?) "
                            + "ON CONFLICT (phone) DO NOTHING",
                    phone, reason, source != null ? source : "CALL");
            log.info("Do-not-call recorded for {} ({}): {}", phone, source, reason);
        } catch (Exception e) {
            log.warn("Do-not-call write failed for {}: {}", phone, e.getMessage());
        }
    }

    public boolean contains(String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }
        try {
            Long count = jdbc.queryForObject(
                    "SELECT count(*) FROM do_not_call_list WHERE phone = ?", Long.class, phone);
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("Do-not-call lookup failed for {}: {}", phone, e.getMessage());
            return false;
        }
    }
}
