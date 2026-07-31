package uz.murodjon.uysotvoice.donotcall.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.uysotvoice.donotcall.entity.DoNotCall;

import java.time.Instant;
import java.util.List;

/** Spring Data repository for {@link DoNotCall}. */
@Repository
public interface DoNotCallJpaRepository extends JpaRepository<DoNotCall, Long> {

    /** Only an active (not removed) opt-out actually blocks dialling. */
    boolean existsByCompanyIdAndPhoneAndRemovedAtIsNull(long companyId, String phone);

    List<DoNotCall> findByCompanyIdAndRemovedAtIsNull(long companyId, Pageable pageable);

    long countByCompanyIdAndRemovedAtIsNull(long companyId);

    /**
     * {@code add} is idempotent: a client who repeats the request on a second call
     * must not fail the write. {@code ON CONFLICT ... DO UPDATE} keeps that atomic under
     * concurrent inserts, which a check-then-insert through {@link JpaRepository#save}
     * cannot guarantee — and it doubles as the "re-opt-out after removal" path: the
     * unique constraint is on {@code (company_id, phone)} regardless of {@code
     * removed_at}, so a phone removed once and blocked again must reactivate the
     * existing row (clearing {@code removed_at}/{@code removed_by}) rather than silently
     * no-op and leave the new opt-out request unrecorded.
     */
    @Modifying @Transactional
    @Query(value = "INSERT INTO do_not_call_list(phone, reason, source, created_at, company_id) "
            + "VALUES (:phone, :reason, :source, :createdAt, :companyId) "
            + "ON CONFLICT (company_id, phone) DO UPDATE SET "
            + "reason = EXCLUDED.reason, source = EXCLUDED.source, created_at = EXCLUDED.created_at, "
            + "removed_at = NULL, removed_by = NULL", nativeQuery = true)
    void upsert(@Param("phone") String phone, @Param("reason") String reason,
               @Param("source") String source, @Param("createdAt") Instant createdAt,
               @Param("companyId") long companyId);

    /** "Ro'yxatdan chiqarish" (§10.8) — soft-delete; a no-op if already removed or never listed. */
    @Modifying @Transactional
    @Query("UPDATE DoNotCall d SET d.removedAt = :removedAt, d.removedBy = :removedBy "
            + "WHERE d.companyId = :companyId AND d.phone = :phone AND d.removedAt IS NULL")
    int remove(@Param("companyId") long companyId, @Param("phone") String phone,
              @Param("removedAt") Instant removedAt, @Param("removedBy") String removedBy);
}
