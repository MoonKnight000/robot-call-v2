package uz.murodjon.robotcallv2.company.domain.entity;

import uz.murodjon.robotcallv2.company.domain.enums.CompanyStatus;
import uz.murodjon.robotcallv2.role.domain.enums.Feature;
import uz.murodjon.robotcallv2.shared.integration.IntegrationProvider;

import java.time.Instant;
import java.util.Set;

/**
 * Collects a {@link Company}'s fields; obtained from {@link Company#builder()} or, to change a
 * few fields of an existing company, {@link Company#toBuilder()}. Anything not set is what a
 * company not saved yet has: id 0, no status, nothing switched off.
 */
public class CompanyBuilder {

    private long id;
    private String name;
    private CompanyStatus status;
    private Instant createdAt;
    private Long logoFileId;
    private String address;
    private Integer maxConcurrentCalls;
    private Set<Feature> disabledFeatures;
    private Set<IntegrationProvider> disabledIntegrations;
    private Instant expiresAt;

    CompanyBuilder() {
    }

    CompanyBuilder(Company source) {
        this.id = source.id();
        this.name = source.name();
        this.status = source.status();
        this.createdAt = source.createdAt();
        this.logoFileId = source.logoFileId();
        this.address = source.address();
        this.maxConcurrentCalls = source.maxConcurrentCalls();
        this.disabledFeatures = source.disabledFeatures();
        this.disabledIntegrations = source.disabledIntegrations();
        this.expiresAt = source.expiresAt();
    }

    public CompanyBuilder id(long id) {
        this.id = id;
        return this;
    }

    public CompanyBuilder name(String name) {
        this.name = name;
        return this;
    }

    public CompanyBuilder status(CompanyStatus status) {
        this.status = status;
        return this;
    }

    public CompanyBuilder createdAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public CompanyBuilder logoFileId(Long logoFileId) {
        this.logoFileId = logoFileId;
        return this;
    }

    public CompanyBuilder address(String address) {
        this.address = address;
        return this;
    }

    public CompanyBuilder maxConcurrentCalls(Integer maxConcurrentCalls) {
        this.maxConcurrentCalls = maxConcurrentCalls;
        return this;
    }

    public CompanyBuilder disabledFeatures(Set<Feature> disabledFeatures) {
        this.disabledFeatures = disabledFeatures;
        return this;
    }

    public CompanyBuilder disabledIntegrations(Set<IntegrationProvider> disabledIntegrations) {
        this.disabledIntegrations = disabledIntegrations;
        return this;
    }

    public CompanyBuilder expiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
        return this;
    }

    public Company build() {
        return new Company(id, name, status, createdAt, logoFileId, address, maxConcurrentCalls, disabledFeatures,
                disabledIntegrations, expiresAt);
    }
}
