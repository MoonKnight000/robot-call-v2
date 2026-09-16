package uz.murodjon.robotcallv2.memory.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
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

    /** At most {@code batchSize} rows, across every company, last written before {@code cutoff}. */
    @Modifying @Transactional
    @Query(value = "DELETE FROM client_memory WHERE id IN "
            + "(SELECT id FROM client_memory WHERE updated_at < :cutoff LIMIT :batchSize)", nativeQuery = true)
    int deleteNotUpdatedSince(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
