package uz.murodjon.uysotvoice.donotcall.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.company.service.CurrentCompany;
import uz.murodjon.uysotvoice.donotcall.dto.DoNotCallFilter;
import uz.murodjon.uysotvoice.donotcall.dto.DoNotCallRow;
import uz.murodjon.uysotvoice.donotcall.entity.DoNotCall;

import java.time.Instant;
import java.util.List;

/**
 * JPA-backed DAO for {@code do_not_call_list} — the phone-level opt-out list
 * (PROJECT.md §11.4). Keyed by (company, phone) rather than by target so an opt-out
 * survives into campaigns that do not exist yet, without leaking across companies —
 * a number blocked at company A must still be reachable by company B (ROADMAP B.2).
 *
 * <p>{@link #add} is idempotent: a client who repeats the request on a second call
 * must not fail the write. Removal (§10.8 "Ro'yxatdan chiqarish") is a soft-delete —
 * {@link #remove} sets {@code removed_at}/{@code removed_by} rather than deleting the
 * row, matching this codebase's general aversion to destructive SQL.
 */
@Repository
public class DoNotCallRepository {

    private static final Logger log = LoggerFactory.getLogger(DoNotCallRepository.class);

    private final DoNotCallJpaRepository jpa;
    private final CurrentCompany company;

    public DoNotCallRepository(DoNotCallJpaRepository jpa, CurrentCompany company) {
        this.jpa = jpa;
        this.company = company;
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
            jpa.upsert(phone, reason, source != null ? source : "CALL", Instant.now(), company.id());
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
            return jpa.existsByCompanyIdAndPhoneAndRemovedAtIsNull(company.id(), phone);
        } catch (Exception e) {
            log.warn("Do-not-call lookup failed for {}: {}", phone, e.getMessage());
            return false;
        }
    }

    /** Active opt-outs only — a removed one no longer belongs on this list (§10.8). */
    public List<DoNotCallRow> findAll(DoNotCallFilter filter) {
        return jpa.findByCompanyIdAndRemovedAtIsNull(company.id(), filter.pageable()).stream()
                .map(DoNotCallRepository::toRow)
                .toList();
    }

    public long count(DoNotCallFilter filter) {
        return jpa.countByCompanyIdAndRemovedAtIsNull(company.id());
    }

    /**
     * "Ro'yxatdan chiqarish" (§10.8) — lets dialling resume for {@code phone} within the
     * current company. A no-op (returns {@code false}) if the phone was never opted out
     * here or was already removed, so a caller can tell "nothing to remove" from "removed".
     */
    public boolean remove(String phone, String removedBy) {
        int updated = jpa.remove(company.id(), phone, Instant.now(), removedBy);
        return updated > 0;
    }

    private static DoNotCallRow toRow(DoNotCall e) {
        return new DoNotCallRow(e.getId(), e.getPhone(), e.getReason(), e.getSource(), e.getCreatedAt());
    }
}
