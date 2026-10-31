package uz.murodjon.robotcallv2.crm.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** JPA entity for crm_status_mapping: maps a company's call disposition / outcome to its CRM status ID. */
@Entity
@Table(name = "crm_status_mapping")
public class CrmStatusMappingEntity {

    @EmbeddedId
    private CrmStatusMappingId id;

    @Column(name = "status_id", nullable = false)
    private Integer statusId;

    protected CrmStatusMappingEntity() {
    }

    public CrmStatusMappingEntity(CrmStatusMappingId id, Integer statusId) {
        this.id = id;
        this.statusId = statusId;
    }

    public CrmStatusMappingEntity(Long companyId, String disposition, Integer statusId) {
        this(new CrmStatusMappingId(companyId, disposition), statusId);
    }

    public CrmStatusMappingId getId() {
        return id;
    }

    public Long getCompanyId() {
        return id != null ? id.getCompanyId() : null;
    }

    public String getDisposition() {
        return id != null ? id.getDisposition() : null;
    }

    public Integer getStatusId() {
        return statusId;
    }

    public void setStatusId(Integer statusId) {
        this.statusId = statusId;
    }
}
