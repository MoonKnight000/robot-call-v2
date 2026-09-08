package uz.murodjon.robotcallv2.apikey.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "api_key")
public class ApiKeyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "company_id", insertable = false, updatable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "key_prefix", nullable = false, unique = true, length = 32)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    /**
     * Eager because the one call that matters — authenticating a request — needs them
     * immediately and runs outside any session a lazy load could use.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "api_key_scope", joinColumns = @JoinColumn(name = "api_key_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 64)
    private Set<Permission> scopes = EnumSet.noneOf(Permission.class);

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CompanyEntity getCompany() {
        return company;
    }

    public void setCompany(CompanyEntity company) {
        this.company = company;
    }

    public Long getCompanyId() {
        return company != null ? company.getId() : null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public void setKeyHash(String keyHash) {
        this.keyHash = keyHash;
    }

    public Set<Permission> getScopes() {
        return scopes;
    }

    public void setScopes(Set<Permission> scopes) {
        this.scopes = scopes == null || scopes.isEmpty()
                ? EnumSet.noneOf(Permission.class)
                : EnumSet.copyOf(scopes);
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
