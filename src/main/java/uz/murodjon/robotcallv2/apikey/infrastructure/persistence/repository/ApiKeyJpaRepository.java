package uz.murodjon.robotcallv2.apikey.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.murodjon.robotcallv2.apikey.infrastructure.persistence.entity.ApiKeyEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ApiKeyJpaRepository extends JpaRepository<ApiKeyEntity, Long> {

    List<ApiKeyEntity> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    Optional<ApiKeyEntity> findByCompanyIdAndId(Long companyId, Long id);

    Optional<ApiKeyEntity> findByKeyPrefix(String keyPrefix);

    @Modifying
    @Query("UPDATE ApiKeyEntity k SET k.lastUsedAt = :usedAt WHERE k.id = :id")
    void updateLastUsedAt(@Param("id") Long id, @Param("usedAt") Instant usedAt);

    @Modifying
    @Query("UPDATE ApiKeyEntity k SET k.revokedAt = :revokedAt WHERE k.company.id = :companyId AND k.id = :id")
    void updateRevokedAt(@Param("companyId") Long companyId, @Param("id") Long id,
                         @Param("revokedAt") Instant revokedAt);
}
