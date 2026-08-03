package uz.murodjon.uysotvoice.apikey.repository;

import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.apikey.dto.ApiKey;
import uz.murodjon.uysotvoice.apikey.dto.ApiKeyFilter;
import uz.murodjon.uysotvoice.apikey.entity.ApiKeyEntity;
import uz.murodjon.uysotvoice.company.service.CurrentCompany;
import uz.murodjon.uysotvoice.user.enums.UserRole;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** JPA-backed DAO for {@code api_key} (§11 settings). */
@Repository
public class ApiKeyRepository {

    private final ApiKeyJpaRepository jpa;
    private final CurrentCompany company;

    public ApiKeyRepository(ApiKeyJpaRepository jpa, CurrentCompany company) {
        this.jpa = jpa;
        this.company = company;
    }

    /** {@code keyHash}/{@code keyPrefix} are already computed by the service — no raw key here. */
    public long create(String name, UserRole role, String keyHash, String keyPrefix) {
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setCompanyId(company.id());
        entity.setName(name);
        entity.setRole(role);
        entity.setKeyHash(keyHash);
        entity.setKeyPrefix(keyPrefix);
        entity.setCreatedAt(Instant.now());
        return jpa.save(entity).getId();
    }

    /** Scoped to the current company — another company's id reads as missing, not found. */
    public ApiKey find(long id) {
        return jpa.findByIdAndCompanyId(id, company.id()).map(ApiKeyRepository::toRow).orElse(null);
    }

    public List<ApiKey> findAll(ApiKeyFilter filter) {
        return jpa.findByCompanyId(company.id(), filter.pageable()).stream()
                .map(ApiKeyRepository::toRow)
                .toList();
    }

    public long count(ApiKeyFilter filter) {
        return jpa.countByCompanyId(company.id());
    }

    /** No-op if {@code id} does not belong to the current company. */
    public void revoke(long id) {
        jpa.findByIdAndCompanyId(id, company.id()).ifPresent(entity -> {
            entity.setRevokedAt(Instant.now());
            jpa.save(entity);
        });
    }

    /**
     * The auth-filter lookup (§11) — unscoped by {@code CurrentCompany}, see {@link
     * ApiKeyJpaRepository#findByKeyHashAndRevokedAtIsNull}. Also stamps {@code
     * last_used_at} so the list view can show recency; best-effort, not audited.
     */
    public Optional<ApiKey> resolve(String keyHash) {
        Optional<ApiKeyEntity> found = jpa.findByKeyHashAndRevokedAtIsNull(keyHash);
        found.ifPresent(entity -> {
            entity.setLastUsedAt(Instant.now());
            jpa.save(entity);
        });
        return found.map(ApiKeyRepository::toRow);
    }

    private static ApiKey toRow(ApiKeyEntity e) {
        return new ApiKey(e.getId(), e.getCompanyId(), e.getName(), e.getKeyPrefix(), e.getRole(),
                e.getCreatedAt(), e.getLastUsedAt(), e.getRevokedAt());
    }
}
