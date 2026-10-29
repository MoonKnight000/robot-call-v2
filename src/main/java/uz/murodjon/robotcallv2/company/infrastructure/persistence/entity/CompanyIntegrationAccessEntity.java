package uz.murodjon.robotcallv2.company.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.shared.integration.IntegrationProvider;

import java.time.Instant;

/**
 * JPA entity for company_integration_access: whether the platform has an integration provider on for a
 * company. A company with no row for a provider has it on.
 */
@Entity
@Table(name = "company_integration_access")
public class CompanyIntegrationAccessEntity implements CompanySwitchRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IntegrationProvider provider;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public CompanyEntity getCompany() {
        return company;
    }

    public void setCompany(CompanyEntity company) {
        this.company = company;
    }

    public IntegrationProvider getProvider() {
        return provider;
    }

    public void setProvider(IntegrationProvider provider) {
        this.provider = provider;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
