package uz.murodjon.robotcallv2.donotcall.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.donotcall.infrastructure.persistence.entity.DoNotCallEntity;

import java.time.Instant;
import java.util.List;

public interface DoNotCallJpaRepository extends JpaRepository<DoNotCallEntity, Long> {

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO do_not_call_list (phone, reason, source, created_at, removed_at, removed_by, company_id) "
            + "VALUES (:phone, :reason, :source, :createdAt, NULL, NULL, :companyId) "
            + "ON CONFLICT (company_id, phone) DO UPDATE SET "
            + "reason = EXCLUDED.reason, "
            + "source = EXCLUDED.source, "
            + "created_at = EXCLUDED.created_at, "
            + "removed_at = NULL, "
            + "removed_by = NULL",
            nativeQuery = true)
    void upsert(@Param("phone") String phone,
                @Param("reason") String reason,
                @Param("source") String source,
                @Param("createdAt") Instant createdAt,
                @Param("companyId") long companyId);

    boolean existsByCompanyIdAndPhoneAndRemovedAtIsNull(long companyId, String phone);

    List<DoNotCallEntity> findByCompanyIdAndRemovedAtIsNull(long companyId, Pageable pageable);

    long countByCompanyIdAndRemovedAtIsNull(long companyId);

    @Modifying
    @Transactional
    @Query("UPDATE DoNotCallEntity e SET e.removedAt = :removedAt, e.removedBy = :removedBy "
            + "WHERE e.company.id = :companyId AND e.phone = :phone AND e.removedAt IS NULL")
    int remove(@Param("companyId") long companyId,
               @Param("phone") String phone,
               @Param("removedAt") Instant removedAt,
               @Param("removedBy") String removedBy);
}
