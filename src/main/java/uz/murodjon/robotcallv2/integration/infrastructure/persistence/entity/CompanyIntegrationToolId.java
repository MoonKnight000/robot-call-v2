package uz.murodjon.robotcallv2.integration.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** Key of company_integration_tool: one company's choice about one tool. */
@Embeddable
public class CompanyIntegrationToolId implements Serializable {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "tool_id", nullable = false)
    private Long toolId;

    protected CompanyIntegrationToolId() {
    }

    public CompanyIntegrationToolId(Long companyId, Long toolId) {
        this.companyId = companyId;
        this.toolId = toolId;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public Long getToolId() {
        return toolId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CompanyIntegrationToolId that
                && Objects.equals(companyId, that.companyId) && Objects.equals(toolId, that.toolId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(companyId, toolId);
    }
}
