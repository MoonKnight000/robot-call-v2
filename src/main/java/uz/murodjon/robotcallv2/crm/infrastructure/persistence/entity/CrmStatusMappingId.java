package uz.murodjon.robotcallv2.crm.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** Composite primary key for crm_status_mapping: (company_id, disposition). */
@Embeddable
public class CrmStatusMappingId implements Serializable {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "disposition", nullable = false, length = 64)
    private String disposition;

    protected CrmStatusMappingId() {
    }

    public CrmStatusMappingId(Long companyId, String disposition) {
        this.companyId = companyId;
        this.disposition = disposition;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public String getDisposition() {
        return disposition;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CrmStatusMappingId that
                && Objects.equals(companyId, that.companyId)
                && Objects.equals(disposition, that.disposition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(companyId, disposition);
    }
}
