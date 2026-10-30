package uz.murodjon.robotcallv2.integration.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** JPA entity for company_integration_tool: a company's own choice, present only when it made one. */
@Entity
@Table(name = "company_integration_tool")
public class CompanyIntegrationToolEntity {

    @EmbeddedId
    private CompanyIntegrationToolId id;

    @Column(nullable = false)
    private boolean enabled;

    protected CompanyIntegrationToolEntity() {
    }

    public CompanyIntegrationToolEntity(CompanyIntegrationToolId id, boolean enabled) {
        this.id = id;
        this.enabled = enabled;
    }

    public CompanyIntegrationToolId getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
