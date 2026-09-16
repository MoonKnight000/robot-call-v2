package uz.murodjon.robotcallv2.storage.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import uz.murodjon.robotcallv2.storage.infrastructure.persistence.entity.StoredFileEntity;

import java.time.Instant;
import java.util.List;

@Repository
public interface StoredFileJpaRepository extends JpaRepository<StoredFileEntity, Long> {

    /**
     * Files of one category written before {@code cutoff}, at most {@code batchSize}, leaving
     * out call recordings — a call's recording is kept for as long as its agent says, and
     * the retention sweep removes it through the call.
     */
    @Query(value = "SELECT f.* FROM stored_file f WHERE f.category = :category AND f.created_at < :cutoff "
            + "AND NOT EXISTS (SELECT 1 FROM call_attempt a WHERE a.recording_file_id = f.id) "
            + "ORDER BY f.id LIMIT :batchSize", nativeQuery = true)
    List<StoredFileEntity> findOlderThanExceptCallRecordings(@Param("category") String category,
                                                             @Param("cutoff") Instant cutoff,
                                                             @Param("batchSize") int batchSize);
}
