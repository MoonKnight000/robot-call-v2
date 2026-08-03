package uz.murodjon.uysotvoice.apikey.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.apikey.entity.ApiKeyEntity;

import java.util.List;
import java.util.Optional;

/** Spring Data repository for {@link ApiKeyEntity}. */
@Repository
public interface ApiKeyJpaRepository extends JpaRepository<ApiKeyEntity, Long> {

    Optional<ApiKeyEntity> findByIdAndCompanyId(long id, long companyId);

    List<ApiKeyEntity> findByCompanyId(long companyId, Pageable pageable);

    long countByCompanyId(long companyId);

    /**
     * The runtime lookup {@code ApiKeyFilter} makes on every request carrying an
     * {@code X-Api-Key} header — deliberately unscoped by {@code CurrentCompany}: the
     * presented key is what tells the request which company it belongs to, not the
     * other way around.
     */
    Optional<ApiKeyEntity> findByKeyHashAndRevokedAtIsNull(String keyHash);
}
