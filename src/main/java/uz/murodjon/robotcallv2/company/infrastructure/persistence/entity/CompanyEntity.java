package uz.murodjon.robotcallv2.company.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import uz.murodjon.robotcallv2.company.domain.enums.CompanyStatus;
import uz.murodjon.robotcallv2.role.domain.enums.Feature;
import uz.murodjon.robotcallv2.shared.integration.IntegrationProvider;
import uz.murodjon.robotcallv2.storage.infrastructure.persistence.entity.StoredFileEntity;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JPA entity for company (ROADMAP B.1).
 */
@Entity
@Table(name = "company")
public class CompanyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompanyStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "logo_file_id")
    private StoredFileEntity logoFile;

    @Column(name = "address")
    private String address;

    @Column(name = "max_concurrent_calls")
    private Integer maxConcurrentCalls;

    // Eager on purpose, like a role's permissions: every use of a company needs its switches, and there are
    // a handful of rows. SUBSELECT: a listed page of companies loads all of them in one query.
    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    private Set<CompanyFeatureEntity> features = new LinkedHashSet<>();

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    private Set<CompanyIntegrationAccessEntity> integrationAccess = new LinkedHashSet<>();

    @Column(name = "expires_at")
    private Instant expiresAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CompanyStatus getStatus() {
        return status;
    }

    public void setStatus(CompanyStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public StoredFileEntity getLogoFile() {
        return logoFile;
    }

    public void setLogoFile(StoredFileEntity logoFile) {
        this.logoFile = logoFile;
    }

    public Long getLogoFileId() {
        return logoFile != null ? logoFile.getId() : null;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Integer getMaxConcurrentCalls() {
        return maxConcurrentCalls;
    }

    public void setMaxConcurrentCalls(Integer maxConcurrentCalls) {
        this.maxConcurrentCalls = maxConcurrentCalls;
    }

    public Set<CompanyIntegrationAccessEntity> getIntegrationAccess() {
        return integrationAccess;
    }

    /** Integrations the platform switched off. */
    public Set<IntegrationProvider> getDisabledIntegrations() {
        return integrationAccess.stream()
                .filter(row -> !row.isEnabled())
                .map(CompanyIntegrationAccessEntity::getProvider)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(IntegrationProvider.class)));
    }

    public Set<CompanyFeatureEntity> getFeatures() {
        return features;
    }

    /** Features the platform switched off. */
    public Set<Feature> getDisabledFeatures() {
        return features.stream()
                .filter(row -> !row.isEnabled())
                .map(CompanyFeatureEntity::getFeature)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Feature.class)));
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
