package uz.murodjon.robotcallv2.memory.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.memory.infrastructure.persistence.entity.ClientMemoryEntity;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface ClientMemoryJpaRepository extends JpaRepository<ClientMemoryEntity, Long> {

    Optional<ClientMemoryEntity> findByCompanyIdAndPhone(long companyId, String phone);

    /** Creates an empty row unless one exists; the unique (company_id, phone) index makes this race-free. */
    @Modifying
    @Query(value = "INSERT INTO client_memory (company_id, phone) VALUES (:companyId, :phone) "
            + "ON CONFLICT (company_id, phone) DO NOTHING", nativeQuery = true)
    void insertEmptyIfMissing(@Param("companyId") long companyId, @Param("phone") String phone);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM ClientMemoryEntity m WHERE m.companyId = :companyId AND m.phone = :phone")
    Optional<ClientMemoryEntity> findByCompanyIdAndPhoneForUpdate(@Param("companyId") long companyId,
                                                                 @Param("phone") String phone);

    @Modifying
    @Query(value = "DELETE FROM client_memory WHERE company_id = :companyId AND phone = :phone", nativeQuery = true)
    int deleteByCompanyIdAndPhone(@Param("companyId") long companyId, @Param("phone") String phone);

    /** At most {@code batchSize} rows, across every company, last written before {@code cutoff}. */
    @Modifying @Transactional
    @Query(value = "DELETE FROM client_memory WHERE id IN "
            + "(SELECT id FROM client_memory WHERE updated_at < :cutoff LIMIT :batchSize)", nativeQuery = true)
    int deleteNotUpdatedSince(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
