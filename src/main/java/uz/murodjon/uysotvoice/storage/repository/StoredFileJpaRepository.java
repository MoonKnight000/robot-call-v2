package uz.murodjon.uysotvoice.storage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.storage.entity.StoredFileEntity;
import uz.murodjon.uysotvoice.storage.enums.FileCategory;

import java.time.Instant;
import java.util.List;

/** Spring Data repository for {@link StoredFileEntity}. */
@Repository
public interface StoredFileJpaRepository extends JpaRepository<StoredFileEntity, Long> {

    @Query("SELECT f FROM StoredFileEntity f WHERE f.category = :category AND f.createdAt < :cutoff")
    List<StoredFileEntity> findByCategoryAndCreatedAtBefore(@Param("category") FileCategory category,
                                                             @Param("cutoff") Instant cutoff);
}
