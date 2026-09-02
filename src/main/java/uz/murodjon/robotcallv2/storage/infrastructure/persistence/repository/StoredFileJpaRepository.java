package uz.murodjon.robotcallv2.storage.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import uz.murodjon.robotcallv2.storage.domain.enums.FileCategory;
import uz.murodjon.robotcallv2.storage.infrastructure.persistence.entity.StoredFileEntity;

import java.time.Instant;
import java.util.List;

@Repository
public interface StoredFileJpaRepository extends JpaRepository<StoredFileEntity, Long> {

    @Query("SELECT f FROM StoredFileEntity f WHERE f.category = :category AND f.createdAt < :cutoff")
    List<StoredFileEntity> findByCategoryAndCreatedAtBefore(@Param("category") FileCategory category,
                                                           @Param("cutoff") Instant cutoff);
}
